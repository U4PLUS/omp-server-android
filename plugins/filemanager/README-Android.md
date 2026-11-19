# FileManager - open.mp Android arm64 移植

来源：[JaTochNietDan/SA-MP-FileManager](https://github.com/JaTochNietDan/SA-MP-FileManager) v1.4

## Android arm64 移植要点

唯一移植障碍是 **64 位指针截断**：
- 原版把 `FILE*`（fopen）和 `DirectoryStream*`（dir_open）裸指针直接强转成 32 位 Pawn cell 返回
- arm64 指针 8 字节、Pawn cell 4 字节 → 返回截断、回转错误 → 崩溃
- 修复：句柄表（`unordered_map<cell, FILE*>` / `<cell, DirectoryStream*>`），f_open/dir_open 存指针返回递增 handle id，f_close/f_read/f_write/dir_list/dir_close 查表取指针

共改 8 处 native（f_open / f_close / f_read / f_write / dir_open / dir_open_sorted / dir_list / dir_close），其余 13 个 native（路径字符串操作）无需改动。

## 构建（arm64）

需要 arm64 原生交叉编译工具链（armtc clang + NDK27）。

```bash
./build-android.sh
# 输出: filemanager.so (aarch64)
```

## 部署

- `filemanager.so` → server 目录，在 config.json `plugins` 里加 `"filemanager"`
- `filemanager.inc` → `pawncc/include/`，脚本里 `#include <filemanager>`

## Native 函数

- `file_move` / `file_delete` / `file_create` / `file_write` / `file_read` / `file_copy` / `file_exists` / `file_log`
- `dir_create` / `dir_delete` / `dir_exists` / `dir_open` / `dir_open_sorted` / `dir_list` / `dir_close`
- `f_open` / `f_close` / `f_read` / `f_write`
