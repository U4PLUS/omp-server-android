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

#include "Effect.h"

class EffectManager {
public: 
    template<class ParametersType>
    static Effect* CreateEffect(uint32_t number, int priority, const ParametersType& parameters) 
    {
        Effect* effect = new (std::nothrow) Effect(number, priority, parameters);
        RegisterEffect(effect);
        return effect;
    }

    static uint32_t RegisterEffect(Effect* effect);   // 返回句柄 id
    static void UnregisterEffect(Effect* effect);
    static bool IsValidEffect(Effect* effect);          // 组件内部（完整指针）
    static bool IsValidEffectHandle(uint32_t handle);   // pawn 层句柄
    static Effect* GetEffect(uint32_t handle);          // 句柄 -> Effect*
    static uint32_t GetEffectHandle(Effect* effect);    // Effect* -> 句柄

private:
    static std::set<Effect*> effects_;
    static std::unordered_map<uint32_t, Effect*> handles_;
    static std::unordered_map<Effect*, uint32_t> reverse_;
    static uint32_t nextId_;
    static std::mutex mutex_;
};