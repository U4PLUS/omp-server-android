/*
	This is a SampVoice project file
	Author: CyberMor <cyber.mor.2020@gmail.ru>
	open.mp version author: AmyrAhmady (iAmir) <hhm6@yahoo.com>

	See more here https://github.com/AmyrAhmady/sampvoice

	Copyright (c) Daniel (CyberMor) 2020 All rights reserved
*/

#pragma once

#include <set>
#include <unordered_map>
#include <cstdint>
#include <mutex>

#include "Stream.h"

// [Android] 句柄表：解决 64 位指针截断到 32 位 pawn cell 导致的跨架构缺陷。
// 脚本里用 32 位整数句柄(id)，组件内部经句柄表映射回 64 位 Stream*。
class StreamManager {
public:
    static uint32_t RegisterStream(Stream* streamese);   // 返回句柄 id
    static void UnregisterStream(Stream* stream);
    static bool IsValidStream(Stream* stream);            // 组件内部（完整指针）
    static bool IsValidHandle(uint32_t handle);           // pawn 层句柄有效性
    static Stream* GetStream(uint32_t handle);            // 句柄 -> Stream*
    static uint32_t GetHandle(Stream* stream);            // Stream* -> 句柄

private:
    static std::set<Stream*> streams_;
    static std::unordered_map<uint32_t, Stream*> handles_;
    static std::unordered_map<Stream*, uint32_t> reverse_;
    static uint32_t nextId_;
    static std::mutex mutex_;
};
