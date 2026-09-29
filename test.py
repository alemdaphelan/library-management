import requests

def test_login(username, password):
    print(f"Testing {username}...")
    url = "http://localhost:8080/api/v1/auth/login"
    payload = {
        "email": username,
        "password": password
    }
    response = requests.post(url, json=payload)
    if response.status_code == 200:
        data = response.json()
        print(f"  [SUCCESS] role={data.get('role')}")
        return data
    else:
        print(f"  [ERROR] {response.status_code}: {response.text[:100]}")

print("--- Testing Logins ---")
test_login("admin@huit.edu.vn", "admin123")
test_login("librarian@huit.edu.vn", "admin123")
test_login("2001234030", "admin123")  # Student login by MSSV
test_login("student@student.huit.edu.vn", "admin123")  # Student login by email
test_login("accountant@huit.edu.vn", "admin123")
test_login("treasurer@huit.edu.vn", "admin123")
