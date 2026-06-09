# 敏感字段检测工作流

## 一、项目概述

### 1.1 目标

对输入的数据字典（系统、表、字段中英文名）进行质检和敏感识别：

1. **质检拦截**：过滤字段中文名为空、含乱码、无中文、同表重复等质量问题
2. **敏感识别**：判断字段是否疑似敏感，并匹配到安全分类目录节点

### 1.2 定位

- 本工具**不做最终安全分类分级**，只做"疑似敏感识别 + 质量拦截"
- 疑似敏感字段的精细判断（中文名是否清晰、能否正确挂载）由后续节点完成
- 九要素为 POC 阶段验证，后续目录会扩展

### 1.3 技术栈

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| Spring Boot | 3.4.3 |
| Spring AI Alibaba | 1.1.2.2 |
| Spring AI OpenAI Starter | 1.1.0 |
| LLM 调用方式 | OpenAI 兼容接口（不使用 DashScope SDK） |

---

## 二、安全分类目录（九要素 v0.1）

### 2.1 目录结构

```
客户信息 > 个人客户 > 个人基本信息
    ├── 姓名
    ├── 性别
    ├── 出生日期
    ├── 证件类型
    ├── 证件号码
    ├── 联系电话
    ├── 联系地址
    ├── 职业
    └── 国籍
```

### 2.2 各节点覆盖范围

| 目录节点 | 覆盖的数据项 |
|---------|------------|
| 姓名 | 客户姓名、曾用名、英文名、投保人姓名、被保人姓名、受益人姓名、报案人姓名等 |
| 性别 | 性别 |
| 出生日期 | 出生日期、出生年月、生日、年龄等 |
| 证件类型 | 证件类型、证件种类、证件类型代码等 |
| 证件号码 | 身份证号、护照号、军官证号、港澳通行证号等 |
| 联系电话 | 手机号、固定电话、紧急联系电话等 |
| 联系地址 | 通讯地址、户籍地址、居住地址、省、市、区、街道、邮编等 |
| 职业 | 职业代码、职业名称、职业类别等 |
| 国籍 | 国籍代码、国籍名称等 |

### 2.3 设计原则

- **按数据本质分类，不按业务角色分类**：投保人姓名、被保人姓名都挂到"姓名"节点
- **目录节点是固定枚举**：LLM 只能从以下值中选择输出：`姓名`、`性别`、`出生日期`、`证件类型`、`证件号码`、`联系电话`、`联系地址`、`职业`、`国籍`、`不确定`

---

## 三、工作流设计

### 3.1 整体流程

```
数据字典输入（全量字段）
    │
    ▼
步骤1：质检规则（代码，Java + 正则）
    │  - A000003：字段中文名禁止为空
    │  - A000001：字段中文名含乱码
    │  - A000002：字段中文名需包含中文描述
    │  - A000004：同一表内字段中文名必须唯一
    │  - 标记拦截状态，所有字段继续往下走
    ▼
步骤2：关键词匹配（代码）
    │  - 命中排除词 → 标记"非敏感"
    │  - 命中敏感词 → 标记"疑似敏感" + 目录节点
    │  - 都没命中 → 进入下一步
    ▼
步骤3：LLM 判断（Spring AI，OpenAI 兼容接口）
    │  - 逐条输入未决字段
    │  - 判断是否疑似敏感 + 匹配目录节点 + 原因
    │  - 解析失败重试 3 次，仍失败标记"不确定-LLM解析失败"
    ▼
步骤4：结果合并（代码）
    │  - 汇总质检标记 + 敏感标记
    │  - 输出结果表
    ▼
输出
```

### 3.2 步骤间数据流转

所有字段共享同一个数据结构，每个步骤往上追加标记：

```
输入字段 → [质检标记] → [敏感标记] → 输出
```

每个字段在流转过程中累积触发的规则，不因某步拦截而终止后续步骤。

---

## 四、质检规则详细设计

### 4.1 规则清单

| 序号 | 规则编号 | 类型 | 级别 | 规则描述 | 执行顺序 |
|------|---------|------|------|---------|---------|
| 1 | A000003 | 完整性 | 红标 | 字段中文名禁止为空 | 1 |
| 2 | A000001 | 规范性 | 红标 | 字段中文名含乱码 | 2 |
| 3 | A000002 | 规范性 | 红标 | 字段中文名需包含中文描述 | 3 |
| 4 | A000004 | 一致性 | 红标 | 同一表内字段中文名必须唯一 | 4 |

### 4.2 A000003：字段中文名禁止为空

```java
public boolean check(String fieldCn) {
    return fieldCn == null || fieldCn.trim().isEmpty();
}
```

### 4.3 A000001：字段中文名含乱码

```java
public boolean check(String fieldCn) {
    if (fieldCn == null) return false; // A000003 已处理

    // 编码损坏型乱码（UTF-8 被当 Latin-1 解析）
    if (Pattern.compile("[\\u00c0-\\u00ff]{2,}").matcher(fieldCn).find()) return true;
    // Unicode 替换字符
    if (fieldCn.contains("\uFFFD")) return true;
    // 不可见控制字符
    if (Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]").matcher(fieldCn).find()) return true;
    // 私有使用区字符
    if (Pattern.compile("[\\uE000-\\uF8FF]").matcher(fieldCn).find()) return true;

    return false;
}
```

### 4.4 A000002：字段中文名需包含中文描述

```java
public boolean check(String fieldCn) {
    if (fieldCn == null) return false; // A000003 已处理
    // 至少包含一个中文字符
    return !Pattern.compile("[\\u4e00-\\u9fff]").matcher(fieldCn).find();
}
```

### 4.5 A000004：同一表内字段中文名必须唯一

```java
public Map<String, List<String>> check(List<Field> fields) {
    // 按 系统+表 分组
    Map<String, List<String>> tableFields = new HashMap<>();
    for (Field f : fields) {
        String key = f.getSystemName() + "|" + f.getTableName();
        tableFields.computeIfAbsent(key, k -> new ArrayList<>()).add(f.getFieldCn());
    }

    Map<String, List<String>> duplicates = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : tableFields.entrySet()) {
        Map<String, Long> countMap = entry.getValue().stream()
            .filter(name -> name != null && !name.trim().isEmpty())
            .collect(Collectors.groupingBy(String::trim, Collectors.counting()));

        List<String> dups = countMap.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();

        if (!dups.isEmpty()) {
            duplicates.put(entry.getKey(), dups);
        }
    }
    return duplicates;
}
```

注意：A000004 对重复的字段**全部标记**，不做取舍。

---

## 五、敏感识别详细设计（B000001）

### 5.1 规则定义

| 规则编号 | 描述 |
|---------|------|
| B000001 | 疑似敏感字段检测：判断字段是否疑似涉及安全目录中的敏感数据项 |

### 5.2 关键词匹配

#### 5.2.1 排除词清单

命中以下关键词的字段，直接标记为**非敏感**，不进入 LLM。仅排除技术类字段：

```
# 技术类 / 系统类字段
修改时间, 创建时间, 更新时间, 操作时间, 修改日期, 创建日期, 更新日期,
创建人, 修改人, 操作人, 操作员,
版本号, 数据状态, 删除标志, 是否有效, 是否删除,
排序, 排序号, 序号,
备注, 说明, 描述, 摘要
```

#### 5.2.2 敏感词清单

命中以下关键词的字段，直接标记为**疑似敏感**，同时记录匹配到的目录节点：

```
# 姓名节点
姓名 → 姓名
客户名 → 姓名
投保人姓名 → 姓名
被保人姓名 → 姓名
受益人姓名 → 姓名
报案人姓名 → 姓名
曾用名 → 姓名
英文名 → 姓名
name, cust_name, app_name, ins_name, bnf_name → 姓名

# 性别节点
性别 → 性别
sex, gender → 性别

# 出生日期节点
出生日期 → 出生日期
出生年月 → 出生日期
生日 → 出生日期
birthday, birth_date, birth_dt, dob → 出生日期

# 证件类型节点
证件类型 → 证件类型
证件种类 → 证件类型
cert_type, id_type, doc_type → 证件类型

# 证件号码节点
身份证号 → 证件号码
证件号码 → 证件号码
证件号 → 证件号码
护照号 → 证件号码
cert_no, id_no, id_card, id_number → 证件号码

# 联系电话节点
手机号 → 联系电话
手机 → 联系电话
电话 → 联系电话
联系电话 → 联系电话
固定电话 → 联系电话
mobile, phone, tel, cell_phone, phone_no → 联系电话

# 联系地址节点
地址 → 联系地址
住址 → 联系地址
通讯地址 → 联系地址
联系地址 → 联系地址
居住地址 → 联系地址
户籍地址 → 联系地址
address, addr, home_addr, mail_addr, contact_addr → 联系地址

# 职业节点
职业 → 职业
职业代码 → 职业
occupation, job, profession → 职业

# 国籍节点
国籍 → 国籍
nationality, country, citizen → 国籍
```

#### 5.2.3 匹配优先级

```
1. 先匹配排除词 → 命中则直接"非敏感"，结束
2. 再匹配敏感词（先匹配中文名，再匹配英文名）→ 命中则"疑似敏感"+ 目录节点
3. 都没命中 → 交 LLM
```

中文名和英文名都命中但节点不同时，以中文名为准。

### 5.3 LLM 判断

#### 5.3.1 触发条件

关键词排除词和敏感词都没命中的字段，进入 LLM 判断。

#### 5.3.2 Prompt 设计

```
## 角色

你是数据安全分类专家。根据给定的安全分类目录，判断数据字典中的字段是否疑似涉及目录中的敏感数据项。

## 安全分类目录

客户信息 > 个人客户 > 个人基本信息：
- 姓名：客户姓名、曾用名、英文名、投保人/被保人/受益人姓名等
- 性别
- 出生日期：出生日期、出生年月、生日、年龄等
- 证件类型：证件类型、证件种类等
- 证件号码：身份证号、护照号、军官证号等
- 联系电话：手机号、固定电话、紧急联系电话等
- 联系地址：通讯地址、户籍地址、居住地址、省、市、区等
- 职业：职业代码、职业名称等
- 国籍：国籍代码、国籍名称等

## 任务

判断以下字段是否可能涉及上述目录中的某项敏感数据。

判断原则：
- 如果字段含义存在歧义（既可能是敏感的也可能是非敏感的），标记为疑似敏感。例如"日期"可能是出生日期（敏感）也可能是业务处理日期（非敏感），应标记为疑似敏感
- 如果字段明确与安全目录无关（如"产品代码""险种类型"），标记为非敏感，不需要多报
- 综合字段中文名、英文名、所属表名、所属系统来判断
- 字段中文名质量可能较差，需要结合英文名和表名推断
- 如果可能匹配多个节点，选最可能的一个；如果真的无法确定，填"不确定"

## 输入字段

| 系统中文名 | 表中文名 | 表英文名 | 字段中文名 | 字段英文名 |
| {system_cn} | {table_cn} | {table_en} | {field_cn} | {field_en} |

## 输出要求

严格按以下 JSON 格式输出，不要输出其他内容：

```json
{
  "is_suspected_sensitive": true,
  "catalog_node": "姓名",
  "reason": "字段中文名为'名'，结合表名'客户信息表'，可能为客户姓名"
}
```

或：

```json
{
  "is_suspected_sensitive": false,
  "catalog_node": null,
  "reason": "字段中文名为'产品代码'，与安全目录九要素无关"
}
```

catalog_node 的取值范围：姓名、性别、出生日期、证件类型、证件号码、联系电话、联系地址、职业、国籍、不确定。
仅 is_suspected_sensitive 为 true 时需要填 catalog_node。
```

#### 5.3.3 调用方式

逐条调用，每个字段单独发送一次 LLM 请求。

#### 5.3.4 错误处理

- JSON 解析失败 → 重试，最多 3 次
- 3 次仍失败 → 该字段标记为：

```json
{
  "is_suspected_sensitive": true,
  "catalog_node": "不确定",
  "reason": "LLM解析失败，标记为疑似敏感待人工确认"
}
```

LLM 解析失败的字段标记为疑似敏感，避免漏报。

---

## 六、输出设计

### 6.1 结果表

每个字段一行，质检规则和敏感识别结果合并在同一张表中。

| 字段名 | 说明 |
|--------|------|
| batch_no | 扫描批次号，如 20260428_01 |
| system_cn | 系统中文名 |
| system_en | 系统英文名 |
| table_cn | 表中文名 |
| table_en | 表英文名 |
| field_cn | 字段中文名 |
| field_en | 字段英文名 |
| quality_rules | 触发的质检规则，分号分隔，如 `A000003:字段中文名禁止为空;A000001:字段中文名含乱码`，无触发时为空 |
| quality_result | A 规则质检结果：`通过` / `拦截`（仅代表 A000001~A000004 的结果） |
| is_suspected_sensitive | B 节点敏感识别结果：`是` / `否` / `不确定` |
| catalog_node | 匹配的安全目录节点（疑似敏感时），如 `姓名`、`证件号码` |
| sensitive_reason | 敏感判断原因 |
| sensitive_source | 判断来源：`关键词` / `LLM` / `关键词排除` |

### 6.2 输出示例

```
batch_no | system_cn | table_cn | field_cn | field_en | quality_rules | quality_result | is_suspected_sensitive | catalog_node | sensitive_reason | sensitive_source
20260428_01 | 核心系统 | 客户表 | | cert_no | A000003:字段中文名禁止为空 | 拦截 | 是 | 证件号码 | 英文名cert_no命中证件号码关键词 | 关键词
20260428_01 | 核心系统 | 客户表 | 修改时间 | upd_time | | 通过 | 否 | | 命中技术类排除词 | 关键词排除
20260428_01 | 理赔系统 | 理赔表 | 日期 | dt | | 通过 | 是 | 出生日期 | 字段中文名为日期-可能为出生日期 | LLM
20260428_01 | 核心系统 | 保单表 | 产品代码 | prod_code | | 通过 | 否 | | 与安全目录九要素无关 | LLM
```

---

## 七、Spring AI 集成方案

### 7.1 依赖配置

使用 Spring AI OpenAI Starter 通过 OpenAI 兼容接口调用模型，不使用 DashScope SDK。

```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.4.3</spring-boot.version>
    <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba.version>
    <spring-ai.version>1.1.0</spring-ai.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <!-- Spring AI OpenAI（OpenAI 兼容方式调用） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>

    <!-- Spring AI Alibaba（工作流编排等能力） -->
    <dependency>
        <groupId>com.alibaba.cloud.ai</groupId>
        <artifactId>spring-ai-alibaba-starter</artifactId>
        <version>${spring-ai-alibaba.version}</version>
        <exclusions>
            <!-- 排除 DashScope，不使用 DashScope 方式调用 -->
            <exclusion>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```

### 7.2 配置文件

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL}  # 兼容接口地址
      chat:
        options:
          model: ${OPENAI_MODEL:qwen-plus}
          temperature: 0.1  # 低温度，减少随机性
          max-tokens: 1024

# 业务配置
sensitive-detection:
  max-retries: 3          # LLM 解析失败最大重试次数
```

### 7.3 核心类结构

```
com.example.sensitivedetection
├── SensitiveDetectionApplication.java
├── config/
│   └── OpenAiConfig.java              # Spring AI OpenAI 配置
├── model/
│   ├── FieldInput.java                # 输入字段数据结构
│   ├── FieldResult.java               # 字段结果数据结构
│   └── LlmResponse.java              # LLM 返回结构
├── rule/
│   ├── QualityRule.java               # 质检规则接口
│   ├── EmptyFieldCnRule.java          # A000003
│   ├── GarbledFieldCnRule.java        # A000001
│   ├── NoChineseFieldCnRule.java      # A000002
│   └── DuplicateFieldCnRule.java      # A000004
├── sensitive/
│   ├── KeywordMatcher.java            # 关键词匹配（排除词 + 敏感词）
│   └── LlmSensitiveDetector.java      # LLM 敏感识别（逐条调用）
├── workflow/
│   └── DetectionWorkflow.java         # 工作流编排：质检 → 关键词 → LLM → 合并
├── output/
│   └── ResultExporter.java            # 结果导出
└── resources/
    ├── application.yml
    ├── exclude-keywords.txt           # 排除词清单（技术类字段）
    └── sensitive-keywords.csv         # 敏感词 → 目录节点映射
```

---

## 八、工作流编排

使用 Spring AI Alibaba 的工作流能力编排各步骤：

```java
@Component
public class DetectionWorkflow {

    // 步骤1：质检规则（A000001~A000004）
    // 步骤2：关键词匹配（排除 + 敏感）
    // 步骤3：LLM 判断（剩余未决字段，逐条调用）
    // 步骤4：结果合并

    public List<FieldResult> execute(List<FieldInput> inputs) {
        // 1. 质检
        runQualityRules(inputs);

        // 2. 关键词匹配
        keywordMatcher.match(inputs);

        // 3. LLM（只处理关键词未决的字段，逐条调用）
        List<FieldInput> undecided = inputs.stream()
            .filter(f -> f.getSensitiveSource() == null)
            .toList();
        for (FieldInput field : undecided) {
            llmDetector.detect(field);
        }

        // 4. 合并输出
        return buildResults(inputs);
    }
}
```

---

## 九、增量处理（后续）

POC 阶段全量扫描。上线后增量策略：

- 输出结果带 `batch_no`（扫描批次号）
- 后续只处理新增/变更的字段
- 通过比对 `系统+表+字段英文名` 识别变更

---

## 十、POC 验收

### 10.1 验收报告内容

| 板块 | 内容 |
|------|------|
| 数据概况 | 扫描系统数、表数、字段总数、耗时 |
| 质检规则统计 | 各规则触发数量和占比 |
| 敏感识别统计 | 疑似敏感 / 非敏感 / 不确定的数量和占比 |
| 九要素覆盖率 | 各目录节点命中的字段数和系统数 |
| 关键词 vs LLM 比例 | 关键词解决了多少、LLM 解决了多少 |
| 准确率 | 抽样 100~200 条人工复核，统计漏报率和误报率 |
| 典型案例 | 5~10 个有代表性的案例 |

### 10.2 核心指标

- **漏报率**（最重要）：实际敏感但判断为非敏感的比例，目标 < 5%
- **误报率**：实际非敏感但判断为疑似敏感的比例，可接受 < 30%（宁可多报）
- **LLM 解析成功率**：目标 > 95%

---

## 十一、后续扩展路径

| 阶段 | 内容 |
|------|------|
| 第一期（当前） | 九要素目录 + 质检规则 + 敏感识别，POC 验证 |
| 第二期 | 扩展目录（保单、理赔、健康医疗、金融账户等），增加 C 节点（精细判断 + 拦截） |
| 第三期 | 增量处理、人工确认页面、规则回流、结果沉淀 |
