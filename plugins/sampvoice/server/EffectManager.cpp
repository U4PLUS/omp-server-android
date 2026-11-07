/*
	This is a SampVoice project file
	Author: CyberMor <cyber.mor.2020@gmail.ru>
	open.mp version author: AmyrAhmady (iAmir) <hhm6@yahoo.com>

	See more here https://github.com/AmyrAhmady/sampvoice

	Copyright (c) Daniel (CyberMor) 2020 All rights reserved
*/

#include "EffectManager.h"

uint32_t EffectManager::RegisterEffect(Effect* effect) {
    std::lock_guard<std::mutex> lock(mutex_);
    effects_.insert(effect);
    uint32_t id = nextId_++;
    if (id == 0) id = nextId_++;
    handles_[id] = effect;
    reverse_[effect] = id;
    return id;
}

void EffectManager::UnregisterEffect(Effect* effect) {
    std::lock_guard<std::mutex> lock(mutex_);
    effects_.erase(effect);
    auto it = reverse_.find(effect);
    if (it != reverse_.end()) {
        handles_.erase(it->second);
        reverse_.erase(it);
    }
}

bool EffectManager::IsValidEffect(Effect* effect) {
    std::lock_guard<std::mutex> lock(mutex_);
    return effects_.find(effect) != effects_.end();
}

bool EffectManager::IsValidEffectHandle(uint32_t handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    return handles_.find(handle) != handles_.end();
}

Effect* EffectManager::GetEffect(uint32_t handle) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = handles_.find(handle);
    return (it != handles_.end()) ? it->second : nullptr;
}

uint32_t EffectManager::GetEffectHandle(Effect* effect) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = reverse_.find(effect);
    return (it != reverse_.end()) ? it->second : 0;
}

std::set<Effect*> EffectManager::effects_;
std::unordered_map<uint32_t, Effect*> EffectManager::handles_;
std::unordered_map<Effect*, uint32_t> EffectManager::reverse_;
std::mutex EffectManager::mutex_;
uint32_t EffectManager::nextId_ = 1;
