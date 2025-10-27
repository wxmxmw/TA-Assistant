# TA Assistant 插件（F0 原型）

> 面向 IntelliJ IDEA 的轻量教学/助理原型，目前进度统一接口 + Mock 服务 + 工具窗聊天 + 编辑器右键动作。

## 目前项目的实现简介

1. `org.ta.api`：统一接口与模型（`KnowledgeStore`、`RetrievedChunk`、`LLMClient`、`RagService`、`Answer`、`Citation`）。
2. `org.ta.mock.MockTAService`：Mock 实现，固定返回演示回复，并在控制台打印用户问题。
3. `org.ta.ui.ChatToolWindowFactory` + `org.ta.ui.ChatPanel`：右侧工具窗聊天界面（输入、发送、消息展示）。
4. `org.ta.actions.AskSelectedCodeAction`：编辑器右键菜单动作，将选中代码作为上下文发送到聊天窗。
具体可见demo视频
## 环境要求

- IntelliJ IDEA 2024.2（或同平台版本）。
- JDK 17 （更高/更低的可能不兼容，比如试过JDK 25 无法成功gradle构建项目）
- 直接在 IntelliJ 中运行 Gradle 任务 `runIde`，无需单独配置命令行运行（见demo演示）。


## 快速运行

Step 1. 用 IntelliJ IDEA 2024.2 打开本项目（Gradle 项目）。IntelliJ 会自动识别为 Gradle 项目，无需额外配置。但有可能会出现项目构建失败（如grable压缩包下载失败），可见B站视频解决问题。  
Step 2. 打开 Gradle 工具窗，展开根项目 `TA` → `intellij` → 双击运行 `runIde`。  
Step 3. 启动的沙箱 IDE 中新建项目后：  

- 右侧工具窗列表找到 `TA Chat`，输入问题点击“发送”，可看到演示回复且控制台打印问题。
- 在任意文件选中代码，右键点击 `Ask Selected Code to TA Chat`，聊天窗将带上下文提问并弹出通知。

## 目录结构

```
TA/
|-- README.md
|-- build.gradle
|-- settings.gradle
|-- gradlew
|-- gradlew.bat
|-- gradle/
|   |-- wrapper/
|       |-- gradle-wrapper.jar
|       |-- gradle-wrapper.properties
|-- src/
    |-- main/
    |   |-- java/
    |   |   |-- org/
    |   |       |-- ta/
    |   |           |-- api/        # 接口与模型
    |   |           |-- mock/       # Mock（模拟）服务实现
    |   |           |-- ui/         # 工具窗与聊天面板
    |   |           |-- actions/    # 编辑器右键动作
    |   |-- resources/
    |       |-- META-INF/
    |           |-- plugin.xml      # 插件注册（工具窗、动作、通知组）
    |-- test/                       # 测试代码，目前没用
        |-- java/
        |-- resources/

```
## 功能点说明

- 目前简单主线流程：问一句 → 有回应 → 控制台打印问题。
- Mock（模拟） 行为：`MockTAService.answer(question, codeContext, topK)` 目前是返回固定文本。

## 后续并行开发具体任务（结合之前计划分工 与当前实际状态）

- A 组：文档处理与索引（A）

  - 解析课程 PDF/讲义，文本清洗与分块（按页、标题、滑窗）。
  - 建立索引与检索（优先 Lucene 关键词检索；时间允许可加向量检索）。
  - 输出检索结果携带来源与页码范围，支持 `topK`。
  - 交付模块：`KnowledgeStore`、`RetrievedChunk`/`Citation` 模型、`addDocuments()`/`search()`。
  - 依赖：Apache Tika；后续可接 LangChain4j 的文本分块工具。
  - 对接点：`RagService.answer(...)` 内调用 `KnowledgeStore.search(question, topK)` 获取检索片段。
- B 组：RAG 管线与 LLM 集成（B）

  - 编排“检索 → 提示构造 → LLM 回答 → 引用后处理 → 无材料 fallback”。
  - 封装 `LLMClient`（可切换供应商，如阿里云百炼/DeepSeek），统一接口。
  - 设计 Prompt 模板：结合问题 + 检索片段 + 上下文；明确引用格式与“无材料”声明。
  - 交付模块：`RagService`、`LLMClient` 抽象、`Answer` 模型（文本/引用/来源类型）。
  - 依赖：A 的 `KnowledgeStore` 接口即可先用伪实现并行开发。
  - 替换策略：现用 `MockTAService` 联调；就绪后以真实 `RagService` 替换，无需改动 UI/动作。
- C 组：IntelliJ 工具窗 UI（C）

  - 创建侧边栏/工具窗聊天界面，支持消息流、加载状态、错误提示与引用卡片展示（逐步完善）。
  - 设置页（预留）：API Key、索引路径、`topK` 等，状态持久化。
  - 与 `RagService` 打通：发送问题、显示回答与来源页码。
  - 交付：`ChatToolWindowFactory`、`ChatPanel`、`plugin.xml` 注册；后续补 `SettingsConfigurable`。
  - //当前状态：`ChatPanel` 与工具窗已实现，使用 Swing 组件避免 VS Code 对 `com.intellij.*` 的误报；在沙箱中可“问一句 → 有回应 → 控制台打印问题”。
- D 组：上下文感知与编辑器右键（D）

  - `AskSelectedCodeAction`：抓取选中代码片段作为上下文发送给 `RagService`。
  - 快捷键绑定、通知提示；基础日志与错误上报（便于演示）。
  - 项目骨架与构建：Gradle、`plugin.xml`、运行配置；简单集成测试串联全链路。
  - 交付：`AskSelectedCodeAction`、上下文封装、通知/日志工具、打包可运行插件。
  - 当前状态：动作已实现并注册，右键文案为 “Ask Selected Code to TA Chat”，能激活工具窗并发送上下文。
- 汇合点与集成方式

  - 统一通过 `RagService.answer(question, Optional<String> codeContext, int topK)` 编排，UI/动作统一调用该接口。
  - 当 A/B 就绪后，将 `org.ta.mock.MockTAService` 替换为真实 `RagService` 实例即可；其他模块无需改动。
