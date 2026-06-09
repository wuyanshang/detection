"""
调用敏感字段检测 Controller 的 Python 脚本
用法：python call_detection.py <excel文件路径>
"""
import sys
import requests
import json


def call_detection(file_path: str, base_url: str = "http://localhost:8080"):
    url = f"{base_url}/api/detection/run"

    with open(file_path, "rb") as f:
        files = {"file": (file_path.split("\\")[-1].split("/")[-1], f,
                          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
        print(f"正在上传文件: {file_path}")
        resp = requests.post(url, files=files, timeout=600)

    if resp.status_code == 200:
        result = resp.json()
        print("=" * 50)
        print(f"检测完成!")
        print(f"  字段总数:     {result.get('totalFields', '-')}")
        print(f"  质检拦截字段: {result.get('interceptedFields', '-')}")
        print(f"  疑似敏感字段: {result.get('suspectedSensitive', '-')}")
        print(f"  表总数:       {result.get('totalTables', '-')}")
        print(f"  质检拦截表:   {result.get('interceptedTables', '-')}")
        print(f"  输出文件:     {result.get('outputFile', '-')}")
        print("=" * 50)
        return result
    else:
        print(f"请求失败 [{resp.status_code}]: {resp.text}")
        return None


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python call_detection.py <excel文件路径> [服务地址]")
        print("示例: python call_detection.py data_dictionary.xlsx")
        print("示例: python call_detection.py data_dictionary.xlsx http://192.168.1.100:8080")
        sys.exit(1)

    excel_path = sys.argv[1]
    server_url = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:8080"
    call_detection(excel_path, server_url)
