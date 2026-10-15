# RAG 知识库管理系统 API 文档

## 接口文档访问地址

启动项目后，访问以下地址查看完整的接口文档：

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **API 文档规范**: http://localhost:8080/v3/api-docs.yaml

## 接口分类

### 1. 知识库管理

| 接口 | 方法 | 路径 | 说明 |
|-----|------|------|------|
| 查询知识库列表 | GET | `/api/v1/kb` | 查询当前用户可访问的知识库列表，包含权限级别 |
| 创建知识库 | POST | `/api/v1/kb` | 创建新的知识库 |

### 2. 文档管理

| 接口 | 方法 | 路径 | 说明 |
|-----|------|------|------|
| 查询文档列表 | GET | `/api/v1/kb/{kbId}/documents` | 查询指定知识库的文档列表 |
| 上传文档 | POST | `/api/v1/kb/{kbId}/documents` | 上传文档到知识库，支持 PDF、DOCX、MD、TXT |
| 删除文档 | DELETE | `/api/v1/kb/{kbId}/documents/{docId}` | 删除指定文档 |
| 下载文档 | GET | `/api/v1/kb/{kbId}/documents/{docId}/download` | 下载原始文件 |

### 3. 索引管理

| 接口 | 方法 | 路径 | 说明 |
|-----|------|------|------|
| 查询索引状态 | GET | `/api/v1/kb/{kbId}/documents/{docId}/status` | 查询文档索引进度 |
| 重建索引 | POST | `/api/v1/kb/{kbId}/documents/{docId}/reindex` | 重新对文档进行分块和向量化 |

## 权限说明

系统使用 Sa-Token 进行权限控制，权限级别如下：

- **ADMIN**: 管理员权限，可以管理知识库、上传文档、删除文档、重建索引
- **WRITE**: 写权限，可以上传文档、删除文档、重建索引
- **READ**: 读权限，可以查看文档列表、下载文档、查询索引状态

## 文档状态说明

文档在处理过程中会经历以下状态：

- **PENDING**: 待处理，已提交索引任务
- **PROCESSING**: 处理中，正在进行分块和向量化
- **DONE**: 完成，索引成功
- **FAILED**: 失败，索引出错

## 支持的文件类型

目前支持以下文件格式：

- **PDF**: `.pdf` 文件
- **Word**: `.docx` 文件
- **Markdown**: `.md` 文件
- **文本**: `.txt` 文件

## 使用示例

### 1. 创建知识库

```bash
POST /api/v1/kb
Content-Type: application/json

{
  "name": "技术文档库",
  "description": "存储技术团队的所有技术文档",
  "departmentId": "dept-001",
  "isPublic": false
}
```

### 2. 上传文档

```bash
POST /api/v1/kb/1/documents
Content-Type: multipart/form-data

file: 技术方案.pdf
```

### 3. 查询索引状态

```bash
GET /api/v1/kb/1/documents/1/status
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "docId": 1,
    "fileName": "技术方案.pdf",
    "status": "DONE",
    "chunkCount": 15,
    "tokenCount": 5000,
    "indexedAt": "2024-01-01T10:30:00",
    "retryCount": 0
  }
}
```

### 4. 重建索引

```bash
POST /api/v1/kb/1/documents/1/reindex
```

## 注意事项

1. 文档上传后会自动提交索引任务，前端可通过轮询 `/status` 接口查询进度
2. 重建索引适用于以下场景：
   - 修改了 RAG 切块参数（chunk.size / overlap）
   - 更换了 Embedding 模型
   - 上次索引失败需要重试
   - 升级了文档解析器
3. 删除文档会同时删除向量数据和 MinIO 中的原始文件，操作不可逆
