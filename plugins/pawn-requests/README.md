# pawn-requests for open.mp Android (arm64)

Southclaws/pawn-requests 的 **open.mp Android (arm64) 移植版**：给 open.mp Android 服务器提供
**HTTP / JSON / WebSocket** 功能。原版是 32 位 SA-MP 插件，本包已交叉编译为 **Android arm64**
独立插件，并针对 open.mp Android 服务器做了多项兼容性修改。

## 设计（依赖由 app 提供，插件单独打包）

- **插件本体**：`requests.so`（单个文件，用户自由选择是否加载）
- **运行时依赖**：`libcpprest.so.2.10`（cpprestsdk）与 `libc++_shared.so`（Android 共享 C++ 运行库）
  **已内置到 open.mp Android app（APK）**，安装时随服务器解压到 `files/server/plugins/`，
  由 app 提供，插件加载时从同目录（`$ORIGIN`）自动找到。
- 因此**用户只需把 `requests.so` 放到服务器的 `plugins/` 目录**即可，无需额外拷贝依赖库。
- 本插件**不兼容通用 SA-MP 服务器**（32 位），仅适用于 open.mp Android app（arm64）。

## 依赖库（供其它插件复用）

这两个依赖是**通用 C++ 库**，不专属 pawn-requests，**其它 open.mp Android 插件可直接复用**：

| 依赖库 | 是什么 | 哪些插件能用 |
|---|---|---|
| `libc++_shared.so` | Android NDK 共享 C++ 运行库（STL/异常/智能指针） | **所有**用 C++ 写的 open.mp 插件 |
| `libcpprest.so.2.10` | cpprestsdk（微软 C++ REST SDK） | **所有**做 HTTP / JSON / WebSocket 的 C++ 插件 |

- 它们已由 app 内置到 `files/server/plugins/`，任何插件 dlopen 时从同目录即可找到。
- 插件作者只需发布自己的 `.so` + 头文件，头文件里 `#include <cpprest/...>` 即可用 libcpprest。

## 内容

- `plugins/requests.so` — 插件本体（open.mp 可加载的 SA-MP 风格插件）
- `include/requests.inc` — Pawn 头文件（**含新增的 `StringToGBK`**）
- `include/http_methods.inc` / `include/status_codes.inc` / `include/pawn_requests_version.inc` — 配套头文件
- `README.md` — 本说明

## 安装到 open.mp Android

1. 把 `plugins/requests.so` 放到 open.mp 服务器的 **plugins/** 目录
   （即与 `omp-server` 同级的 `plugins/` 文件夹；运行时的 `libcpprest.so.2.10`、`libc++_shared.so`
   由 app 提供，已解压在同一个 `plugins/` 目录，插件通过 `$ORIGIN` 找到它们）。
2. 把 `include/*.inc` 全部放到服务器的 **pawncc/include/** 目录（供脚本 `#include <requests>`）。
3. 在 open.mp 配置（config.json 的 `pawn.legacy_plugins`）加入 `requests`：
   ```
   pawn.legacy_plugins = [ "requests" ]
   ```
4. 重启服务器，日志应显示 `Loading plugin: requests`（加载成功且不再崩溃）。

## 用法（Pawn）

在脚本头部：
```pawn
#include <requests>
```

之后即可用 `Request` / `RequestJSON` / `JsonParse` / `JsonObject` / `JsonArray` /
`JsonStringify` / `WebSocketClient` / `WebSocketSend` 等 natives（详见原版 Southclaws/pawn-requests README）。

### 新增 native：StringToGBK

```pawn
native StringToGBK(const src[], dest[], destlen = sizeof(dest));
```

把 **UTF-8** 字符串转成 **GBK**（GB2312/GB18030 子集，BMP）。用于让中文在 SA:MP/客户端正常显示
（服务器日志/HTTP 返回的是 UTF-8，客户端文本显示常为 GBK 编码）。示例：
```pawn
new reply[512];
JsonGetString(message, "content", reply);   // reply 为 UTF-8
new gbkReply[512];
StringToGBK(reply, gbkReply);               // 转 GBK 后发送给客户端
```

### 新增 native：StringToUTF8

```pawn
native StringToUTF8(const src[], dest[], destlen = sizeof(dest));
```

把 **GBK** 字符串转成 **UTF-8**（`StringToGBK` 的逆运算）。用于把客户端输入的 GBK 文本转成
UTF-8 后发送给 HTTP/JSON/AI API（这些 API 期望 UTF-8）。示例：
```pawn
// 客户端输入（如 /ds [内容]）是 GBK
new content[512];
strmid(content, cmdtext, 4, strlen(cmdtext), sizeof(content));
new utf8Content[512];
StringToUTF8(content, utf8Content);          // GBK -> UTF-8 后再发给 API
JsonString(utf8Content)
```

## 移植版修改（相对原版 Southclaws/pawn-requests）

1. **Android arm64 交叉编译**：原版面向 32 位 SA-MP / Windows，本版编译为 Android arm64 独立插件，
   cpprestsdk 依赖为 **JVM-free**（移除 Android JNI 相关，纯 C++ HTTP/WebSocket）。

2. **共享 libc++（关键修复）**：open.mp 服务器（libompserver.so）此前静态链接 libc++（`-fvisibility=hidden`），
   插件（requests + libcpprest）抛 C++ 异常时 unwind 需穿过 open.mp 栈帧，两套 libc++/libunwind 不兼容
   导致 **SIGABRT 崩溃**。修复：open.mp 服务器改用**共享 libc++_shared.so**，统一进程内 libc++，
   插件与服务器 unwind 一致，不再崩溃。因此 app 内置 `libc++_shared.so`。

3. **StringToGBK / StringToUTF8**：新增两个双向编码转换 native（Android NDK 的 iconv 不支持 GBK，
   故预生成 BMP 映射表嵌入插件）：
   - `StringToGBK`：UTF-8→GBK，用于把 AI/HTTP 返回的 UTF-8 转成 GBK 显示给客户端；
   - `StringToUTF8`：GBK→UTF-8，用于把客户端输入（GBK）转成 UTF-8 后再发送给 AI/HTTP API（否则 AI 收不到正常中文）。

4. **WebSocket SSL 证书路径**：`WebSocketClient` / `JsonWebSocketClient` 的 SSL 回调原用
   `/etc/ssl/certs/ca-certificates.crt`（Linux 路径），Android 上不存在导致连接失败；已改为 Android
   的 `/system/etc/security/cacerts`（与 HTTP 客户端一致）。

5. **依赖由 app 提供**：`libcpprest.so.2.10` 与 `libc++_shared.so` 内置到 open.mp Android app，
   解压到 `files/server/plugins/`（open.mp 服务器 dlopen 只搜索服务器目录，app native libs
   该子进程搜不到），插件分发仅需 `requests.so` 一个文件。

## 变更记录

- **v6**：新增 `StringToUTF8`（GBK→UTF-8），修复发送给 AI 的中文乱码（客户端 GBK 输入需先转 UTF-8 再发请求）。
- **v5**：依赖内置 app（libcpprest + libc++_shared 解压到 files/server/plugins/），插件单文件分发；
  新增 `StringToGBK`；修复 WebSocket SSL 证书路径；共享 libc++ 统一 unwind（加载不再崩溃）。
- v4：静态 libc++ + 移除 cpprestsdk `imbue(std::locale::classic())` 调用（locale 状态冲突修复，实验版）。
- v3：移除 `utility::conversions::to_string` 对 `std::locale::classic()` 的调用。
- v2：改用共享 libc++（与服务器静态 libc++ 冲突的临时缓解）。