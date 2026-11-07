/*
	This is a SampVoice project file
	Author: CyberMor <cyber.mor.2020@gmail.ru>
	open.mp version author: AmyrAhmady (iAmir) <hhm6@yahoo.com>

	See more here https://github.com/AmyrAhmady/sampvoice

	Copyright (c) Daniel (CyberMor) 2020 All rights reserved
*/

#include "StreamManager.h"

uint32_t StreamManager::RegisterStream(Stream* stream) {
    std::lock_guard<std::mutex> lock(mutex_);
    streams_.insert(stream);
    uint32_t id = nextId_++;
    if (id == 0) id = nextId_++;  // 跳过 0
    handles_[id] = stream;
    reverse_[stream] = id;
    return id;
}

void StreamManager::UnregisterStream(Stream* stream) {
    std::lock_guard<std::mutex> lock(mutex_);
    streams_.erase(stream);
    auto it = reverse_.find(stream);
    if (it != reverse_.end()) {
        handles_.erase(it->second);
        reverse_.erase(it);
    }
}

bool StreamManager::IsValidStream(Stream* stream) {
    std::lock_guard<std::mutex> lock(mutex_);
    return streams_.find(stream) != streams_.end();
}

bool StreamManager::IsValidHandle(uint32_t handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    return handles_.find(handle) != handles_.end();
}

Stream* StreamManager::GetStream(uint32_t handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = handles_.find(handle);
    return (it != handles_.end()) ? it->second : nullptr;
}

uint32_t StreamManager::GetHandle(Stream* stream) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = reverse_.find(stream);
    return (it != reverse_.end()) ? it->second : 0;
}

std::set<Stream*> StreamManager::streams_;
std::unordered_map<uint32_t, Stream*> StreamManager::handles_;
std::unordered_map<Stream*, uint32_t> StreamManager::reverse_;
std::mutex StreamManager::mutex_;
uint32_t StreamManager::nextId_ = 1;
