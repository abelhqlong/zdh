# iPhone Automation Backend

基于 Java 21 + Spring Boot 3 + Spring Data JPA + PostgreSQL 的 Windows 本地自动化后端骨架。

## 运行环境

- Windows 10/11
- JDK 21
- Maven 3.9+
- PostgreSQL 14+
- 非 headless 桌面会话（AWT Robot/屏幕截图要求）

## 启动

1. 创建数据库 `iphone_automation`
2. 修改 `src/main/resources/application.yml` 中 PostgreSQL 用户密码
3. 修改 `automation.security.secret-key`
4. 运行：

```bash
mvn spring-boot:run
```

## 已包含

- AppleAccount / Workflow / Step / Task JPA Entity
- PostgreSQL 配置
- 账号密码 AES-GCM 密文存储示例
- Windows AWT Robot 鼠标/键盘控制
- 全屏截图
- 坐标映射基础类
- OCR/OpenCV 识别适配接口
- 自动化任务执行骨架
- REST API

## 当前限制

OCR 和 OpenCV 识别接口是预留实现，尚未绑定具体识别引擎；投屏窗口自动定位也需要根据你实际使用的 Windows 投屏软件实现。

Apple 账户的双重认证、验证码或其他安全验证应由用户完成，不做绕过。

## 示例

创建账号：

```http
POST /api/accounts
Content-Type: application/json

{
  "name": "测试账号",
  "appleId": "example@icloud.com",
  "password": "your-password",
  "remark": "测试"
}
```

创建流程：

```http
POST /api/workflows
Content-Type: application/json

{
  "name": "Apple 登录",
  "description": "iPhone Apple Account 登录流程"
}
```

然后可以创建步骤记录（直接通过数据库或后续补充 Step API）。

## 下一步建议

1. 根据你的投屏软件实现“定位投屏窗口 + 截取窗口区域”
2. 接入 OCR
3. 接入 OpenCV 模板匹配
4. 增加 Step CRUD API
5. 增加 WAITING_USER 状态和验证码人工确认界面
6. 增加 Web 前端任务编排

## OCR / 视觉识别（v7）

项目现在增加了基于本机 Tesseract 的 OCR 能力，直接对已经配置的爱思远控截图区域进行识别。

### 新接口

- `GET /api/ocr/status`：查看 OCR 引擎、语言、命令配置
- `POST /api/ocr/region`：识别爱思远控区域
- `POST /api/ocr/screen`：识别整个桌面
- `POST /api/ocr/find`：按文字查找识别结果
- `POST /api/ocr/click`：按文字识别并点击第一个匹配项

### macOS 安装 Tesseract

```bash
brew install tesseract
brew install tesseract-lang
```

默认配置：

```yaml
automation:
  ocr:
    tesseract-command: tesseract
    language: chi_sim+eng
    psm: 6
    timeout-ms: 15000
```

Windows 使用时，把 `tesseract-command` 改成实际 `tesseract.exe` 路径即可。

### 推荐测试顺序

1. 启动 Spring Boot
2. 打开 `http://localhost:8080/`
3. 配置爱思远控区域
4. 点击「区域截图」确认只包含 iPhone 画面
5. 点击「识别爱思远控区域」查看 OCR 结果
6. 输入目标文字，例如「登录」，点击「查找文字」
7. 确认坐标正确后，再使用「识别文字并点击」

OCR 返回的 `screenX/screenY` 已经是 macOS/Windows 桌面的实际坐标，不是爱思远控区域内的相对坐标。

## Workflow 流程自动化（v8）

后端新增流程执行引擎，按步骤定义驱动 OCR + 鼠标/键盘自动操作投屏画面（或 mock/iphone-simulator.html 模拟器）。

### 步骤类型

| actionType | 说明 |
| --- | --- |
| FIND_TEXT | OCR 轮询查找文字（retryCount × timeout），成功返回坐标 |
| CLICK | 有 x/y 直接点击；否则 OCR 找字点击（config.index 指定第几个匹配，如页面同名标题/按钮） |
| INPUT | 键盘输入（内容支持 ${VAR} 变量替换） |
| SWIPE | 鼠标拖拽：从 (x,y) 到 config.toX/toY，config.duration 毫秒 |
| WAIT | 等待 timeout 毫秒 |
| WAIT_CODE | 等待人工完成验证码/授权（config.waitMs 后继续，不做绕过） |
| CHECK | OCR 检查目标文字/ config.keywords 是否出现 |

OCR 匹配策略：精确匹配优先，退化到包含匹配；解析按视觉行合并单词，支持多词按钮整行点击。

### 接口

- `GET /api/workflow/presets`：内置预设列表
- `POST /api/workflow/run-preset/{name}`：运行预设，body `{ "variables": { "APPLE_ID": "...", "PASSWORD": "..." } }`
- `POST /api/workflow/run`：运行自定义流程定义（body 为 {name, description, steps:[...]}）

内置预设：`erase`（刷机）、`activate`（激活+新 Apple ID，从 Hello 页开始）、`poshmark`（Poshmark 通过 Apple ID 注册）、`full`（一键全流程）。

### 使用

1. 全屏打开 `mock/iphone-simulator.html`，用其右下角校准信息配置镜像区域
2. 打开 `http://localhost:8080/`，在「⑦ 流程自动化」选择流程并填写 Apple ID / Password
3. 或直接调接口：`POST /api/workflow/run-preset/erase`

注意：KeyboardController 仅支持 ASCII 字母/数字及部分符号，密码含 `!#$%&*` 等字符会失败；Apple 双重认证等安全验证需由用户人工完成（WAIT_CODE 步骤预留等待时间）。
