# SampVoice (open.mp Android arm64 移植)

经典 SA-MP 语音插件 **sampvoice**（AmyrAhmady/open.mp 版）的 Android arm64 移植。

## 这是什么
- **sampvoice.so**：open.mp **组件**（导出 `ComponentEntryPoint`），arm64
- 提供**语音聊天**：proximity（近距语音）+ 全局语音流，玩家按 Z/B 键说话
- 服务端组件通过 open.mp 网络层收发，语音包用 RakNet bitstream 编码（sampvoice.asi 客户端协议兼容）
- 语音端口：**2020**（UDP，默认；可改 `sampvoice.port` 配置）

## 部署到服务器
1. 把 `sampvoice.so` 放进服务器的 `components/` 目录（open.mp 自动加载）
2. 重启服务器（或刷新组件）
3. 玩家端：sampvoice.asi（SA:MP 0.3.7 / 安卓 sampvoice 客户端）连服务器 `IP:2020`
4. 日志：`server/log.txt`（`[sv:...]` 前缀）

### 可选配置（config.json 顶层）
```json
"sampvoice": {
    "port": 2020,
    "updaterate": 10,
    "threads": 1
}
```
- 若 `sampvoice.port` 未配置，默认用 **2020**（Android 版已改，不依赖 config.json）

## 构建（arm64，本机工具链）
依赖：
- **open.mp SDK**：`open.mp/SDK/include`（本地，组件接口匹配服务器版本）
- **omp-net**：open.mp 网络库（bitstream/packet）
- header-only 库：glm / span-lite / string-view-lite / robin-hood-hashing

`build-android.sh` 说明见同目录；产物 `sampvoice.so`。

## 脚本调用（.inc）
脚本里用 sampvoice 的 native，需要 include：
```pawn
#include <sampvoice>
// SvCreateStream / SvAttachListenerToStream / SvAddKey 等
```
把 `include/sampvoice.inc` 放进服务器的 `pawncc/include/` 目录（编译器 include 路径）。

### 最小脚本示例
```pawn
#include <sampvoice>

new SV_GSTREAM:gstream = SV_NULL;

public OnGameModeInit()
{
    gstream = SvCreateGStream(0xffff0000, "Global");
}

public OnPlayerConnect(playerid)
{
    if (SvGetVersion(playerid) == SV_NULL) return; // 无插件
    SvAddKey(playerid, 0x5A);          // Z 键说话
    SvAttachListenerToStream(gstream, playerid);
}

public OnPlayerActivationKeyPress(playerid, keyid)
{
    if (keyid == 0x5A && gstream) SvAttachSpeakerToStream(gstream, playerid);
}

public OnPlayerActivationKeyRelease(playerid, keyid)
{
    if (keyid == 0x5A && gstream) SvDetachSpeakerFromStream(gstream, playerid);
}
```
## 完整示例脚本（voice.pwn）
同目录 `filterscripts/voice.pwn` 提供一份**可用的语音频道系统**（GBK 编码，直接编译）：
- **公共频道** + 自定义频道（GStream）
- 创建 / 加入 / 退出频道，无人频道自动清理
- 公共频道听筒 / 麦克风开关（支持"双推"到两个频道）
- 查看频道成员、对话菜单管理（`/voice` 打开）
- 按键：**X 或 B** 按住说话

编译方法（PC 上，用 pawncc 包含 sampvoice.inc）：
```
pawncc voice.pwn -o voice.amx
```
把 `voice.amx` 放进服务器的 `filterscripts/`，并在 `server.cfg` 的 `filterscripts` 一行加上 `voice`。

## 源码说明（Android 移植改动）
原版是 SA-MP 0.3.7（32 位），open.mp 版用 open.mp SDK。Android arm64 移植改动：
1. **64 位指针截断**：所有 `reinterpret_cast<uint32_t>(ptr)` / `reinterpret_cast<cell>(ptr)`
   → `static_cast<uint32_t>(reinterpret_cast<uintptr_t>(...))`（协议 ID 保持 32 位）
2. **qsqrt**（x86 内联汇编 `=a`）→ C++ 位操作（int32）
3. **logger.h**：抑制 `-Wformat-security`（非字面量格式化串）
4. **NetHandler**：`sampvoice.port` 未配置时默认 **2020**（原版随机绑定）
5. **-lrt** 移除（Android 无 librt，rt 在 libc）
6. **-m32** 移除（arm64），`-static-libstdc++` → Android libc++_shared
7. **句柄表（本版核心修复）**：`Stream*`/`Effect*` 是 64 位指针，直接塞进 32 位 pawn cell 会截断
   → StreamManager/EffectManager 增加句柄表（脚本用整数 id，组件内部 id↔指针），
   彻底解决 Android arm64 下 attach/转发失败（互相听不到）
8. **attach listener 显式调用基类**：绕过 arm64 vtable 问题，确保玩家能 attach 成听众
9. **DLStream 不再 detach 流主人**：动态局部流自动管理时不再把自己（target）踢出听众
