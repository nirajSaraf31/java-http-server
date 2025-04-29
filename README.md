# Java HTTP Server

A lightweight HTTP/1.1 server built in Java from scratch. It supports basic HTTP methods, persistent connections (`keep-alive`), GZIP compression, and basic file I/O. Designed as a learning project or a minimalist web server for simple use cases.

---

## ✨ Features

- Supports `GET` and `POST` requests
- Persistent connections (Keep-Alive)
- GZIP encoding (`Accept-Encoding: gzip`)
- Dynamic response routing (e.g., `/echo/{text}`, `/user-agent`)
- File storage and retrieval via `/files/{filename}`
- Multithreaded (handles multiple clients concurrently)

---

## 📁 Endpoints

| Endpoint               | Method | Description                                      |
|------------------------|--------|--------------------------------------------------|
| `/`                    | GET    | Returns `200 OK` with empty body                |
| `/echo/{text}`         | GET    | Returns `{text}` as plain text                  |
| `/user-agent`          | GET    | Echoes back the `User-Agent` header             |
| `/files/{filename}`    | GET    | Returns file content from a specified directory |
| `/files/{filename}`    | POST   | Saves request body as a file                    |

---

## 🚀 How to Run

```bash
javac Main.java
java Main --directory ./public
