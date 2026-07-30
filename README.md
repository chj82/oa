# OA 管理系统

OA 管理系统采用前后端分离结构。后端使用 Java 17、Spring Boot、MyBatis-Plus、MySQL、Druid 和 Redis；前端使用 Next.js、React 和 TypeScript。

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+
- Redis 7+

## 初始化数据库

应用启动时不会自动创建或更新数据库结构。首次运行前，由开发或运维人员审核并人工执行：

```bash
mysql -u root -p oa < backend/sql/2026072201-system-init.sql
mysql -u root -p oa < backend/sql/2026073001-system-data-init.sql
```

第一份 SQL 创建数据库结构，第二份 SQL 初始化根部门、默认管理员、系统接口、系统资源及关联。默认管理员为 `admin / 12345678`，首次登录后应立即修改密码。后续 SQL 变更继续存放在 `backend/sql/`，项目不使用 Flyway、Liquibase 或 Spring SQL 自动初始化。

## 启动后端

```bash
export MYSQL_URL='jdbc:mysql://127.0.0.1:3306/oa?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export MYSQL_USERNAME='root'
export MYSQL_PASSWORD='数据库密码'
export REDIS_HOST='127.0.0.1'
export REDIS_PORT='6379'
mvn -pl backend/admin-boot -am spring-boot:run
```

默认服务地址为 `http://localhost:8080`，Swagger UI 地址为 `http://localhost:8080/swagger-ui.html`。

应用启动不创建基础数据，也不扫描 Controller 同步接口目录。新增或修改接口路径时，必须同步新增并人工执行 `backend/sql/` 下的数据变更 SQL。

## 启动前端

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

默认前端地址为 `http://localhost:3000`。登录 Token 仅通过 HttpOnly Cookie `ADMIN_TOKEN` 传输，前端不会读取 Token，也不使用 `Authorization` 请求头。

## 验证

```bash
mvn test
mvn package
cd frontend
npm test
npm run lint
npm run build
```

修改 Java 文件后，必须先使用项目内 Google Java Format 格式化本次修改的文件：

```bash
java -jar tools/google-java-format-1.28.0-all-deps.jar --replace <修改的Java文件>
```

项目协作约束详见 [AGENTS.md](AGENTS.md)，系统设计详见 [Java 后台系统设计](docs/superpowers/specs/2026-07-21-java-admin-system-design.md)。
