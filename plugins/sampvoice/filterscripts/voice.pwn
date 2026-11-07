#include <open.mp>
#include <sampvoice>

#define VOICE_KEY_B 0x42
#define VOICE_KEY_X 0x58

#define MAX_VOICE_CHANNELS 32
#define MAX_VOICE_CHANNEL_NAME 32

#define DIALOG_VOICE_MAIN    5000
#define DIALOG_VOICE_CREATE  5001
#define DIALOG_VOICE_JOIN    5002
#define DIALOG_VOICE_PUBLIC  5003
#define DIALOG_VOICE_MEMBERS 5004

#define COLOR_YELLOW 0xFFFF00FF
#define COLOR_GREEN  0x00FF00FF

enum E_VOICE_CHANNEL {
    vcHandle,
    vcName[MAX_VOICE_CHANNEL_NAME]
};

new SV_GSTREAM:g_GlobalStream = SV_NULL;
new g_VoiceChannels[MAX_VOICE_CHANNELS][E_VOICE_CHANNEL];
new g_VoiceChannelCount = 0;

new SV_GSTREAM:g_PlayerChannel[MAX_PLAYERS];
new bool:g_PlayerSoundEnabled[MAX_PLAYERS];
new bool:g_PlayerVoiceEnabled[MAX_PLAYERS];

stock IsVoiceKey(keyid) {
    return (keyid == VOICE_KEY_B || keyid == VOICE_KEY_X);
}

stock CountPlayersInStream(SV_GSTREAM:stream) {
    if (stream == SV_NULL) return 0;
    new count = 0;
    for (new i = 0; i < MAX_PLAYERS; i++) {
        if (IsPlayerConnected(i) && !IsPlayerNPC(i) && g_PlayerChannel[i] == stream) count++;
    }
    return count;
}

stock FindChannelByName(const name[]) {
    for (new i = 0; i < g_VoiceChannelCount; i++) {
        if (!strcmp(g_VoiceChannels[i][vcName], name, true)) return i;
    }
    return -1;
}

stock FindChannelByHandle(SV_GSTREAM:stream) {
    if (stream == SV_NULL) return -1;
    for (new i = 0; i < g_VoiceChannelCount; i++) {
        if (g_VoiceChannels[i][vcHandle] == stream) return i;
    }
    return -1;
}

stock RemoveChannelAt(idx) {
    if (idx < 0 || idx >= g_VoiceChannelCount) return;
    for (new i = idx; i < g_VoiceChannelCount - 1; i++) {
        g_VoiceChannels[i][vcHandle] = g_VoiceChannels[i+1][vcHandle];
        format(g_VoiceChannels[i][vcName], MAX_VOICE_CHANNEL_NAME, "%s", g_VoiceChannels[i+1][vcName]);
    }
    g_VoiceChannelCount--;
}

stock CleanupEmptyChannel(SV_GSTREAM:stream) {
    if (stream == SV_NULL || stream == g_GlobalStream) return;
    if (CountPlayersInStream(stream) > 0) return;

    new idx = FindChannelByHandle(stream);
    if (idx == -1) return;

    new removedName[MAX_VOICE_CHANNEL_NAME];
    format(removedName, sizeof(removedName), "%s", g_VoiceChannels[idx][vcName]);

    SvDeleteStream(stream);
    RemoveChannelAt(idx);

    printf("[Voice] 频道 \"%s\" 已无人，自动删除。", removedName);
}

// 关键：先更新 g_PlayerChannel，再 detach，最后清理旧频道
stock JoinCustomChannel(playerid, SV_GSTREAM:stream) {
    if (g_PlayerChannel[playerid] == stream) return;

    new SV_GSTREAM:old = g_PlayerChannel[playerid];
    g_PlayerChannel[playerid] = stream;   // ① 先让"自己"离开旧频道的统计

    if (old != SV_NULL && SvGetVersion(playerid) != SV_NULL) {
        SvDetachSpeakerFromStream(old, playerid);   // ② 再 detach
        SvDetachListenerFromStream(old, playerid);
    }

    if (old != SV_NULL) {
        CleanupEmptyChannel(old);                    // ③ 此时自己已不计数
    }

    if (stream == SV_NULL) {
        if (!g_PlayerVoiceEnabled[playerid]) {
            g_PlayerVoiceEnabled[playerid] = true;
            SendClientMessage(playerid, COLOR_YELLOW,
                "[语音] 已回到公共频道，公共频道麦克风已恢复开启。");
        }
        return;
    }

    if (SvGetVersion(playerid) != SV_NULL) {
        SvAttachListenerToStream(stream, playerid);
    }

    if (g_PlayerVoiceEnabled[playerid]) {
        g_PlayerVoiceEnabled[playerid] = false;
        if (SvGetVersion(playerid) != SV_NULL) {
            SvDetachSpeakerFromStream(g_GlobalStream, playerid);
        }
        SendClientMessage(playerid, COLOR_YELLOW,
            "[语音] 已进入自定义频道，公共频道麦克风已自动关闭。");
        SendClientMessage(playerid, COLOR_YELLOW,
            "如需同时向两个频道说话，请在“公共频道设置”中手动开启麦克风。");
    }
}

public OnFilterScriptInit() {
    new streamName[] = "Public Channel";
    g_GlobalStream = SvCreateGStream(0xFFFFFFFF, streamName);
    if (g_GlobalStream == SV_NULL) {
        print("[Voice] 错误: 无法创建公共语音流!");
    } else {
        print("[Voice] 公共语音频道创建成功。");
    }
    return 1;
}

public OnFilterScriptExit() {
    if (g_GlobalStream != SV_NULL) {
        SvDeleteStream(g_GlobalStream);
        g_GlobalStream = SV_NULL;
    }
    for (new i = 0; i < g_VoiceChannelCount; i++) {
        if (g_VoiceChannels[i][vcHandle] != SV_NULL) {
            SvDeleteStream(g_VoiceChannels[i][vcHandle]);
            g_VoiceChannels[i][vcHandle] = SV_NULL;
        }
    }
    g_VoiceChannelCount = 0;
    return 1;
}

public OnPlayerConnect(playerid) {
    g_PlayerChannel[playerid] = SV_NULL;
    g_PlayerSoundEnabled[playerid] = true;
    g_PlayerVoiceEnabled[playerid] = true;

    if (g_GlobalStream == SV_NULL) return 1;

    if (SvGetVersion(playerid) == SV_NULL) {
        SendClientMessage(playerid, -1, "未检测到 SampVoice 插件，语音功能不可用。");
        return 1;
    }
    if (SvHasMicro(playerid) == SV_FALSE) {
        SendClientMessage(playerid, -1, "未检测到麦克风，无法使用语音。");
        return 1;
    }

    SvAddKey(playerid, VOICE_KEY_X);
    SvAddKey(playerid, VOICE_KEY_B);
    SvAttachListenerToStream(g_GlobalStream, playerid);

    SendClientMessage(playerid, COLOR_GREEN, "本服务器添加语音功能，已自动加入公共频道");
    SendClientMessage(playerid, 0xFFFFFFFF, "使用 /voice 打开语音管理，按住 X 或 B 说话");
    return 1;
}

public OnPlayerDisconnect(playerid, reason) {
    // 同理：先把玩家从频道统计中摘出去，再 detach，最后 cleanup
    new SV_GSTREAM:old = g_PlayerChannel[playerid];
    g_PlayerChannel[playerid] = SV_NULL;

    if (SvGetVersion(playerid) != SV_NULL) {
        if (g_GlobalStream != SV_NULL) {
            SvDetachSpeakerFromStream(g_GlobalStream, playerid);
            SvDetachListenerFromStream(g_GlobalStream, playerid);
        }
        if (old != SV_NULL) {
            SvDetachSpeakerFromStream(old, playerid);
            SvDetachListenerFromStream(old, playerid);
        }
    }

    if (old != SV_NULL) {
        CleanupEmptyChannel(old);
    }
    return 1;
}

public SV_VOID:OnPlayerActivationKeyPress(SV_UINT:playerid, SV_UINT:keyid) {
    if (!IsVoiceKey(keyid)) return;
    if (SvGetVersion(playerid) == SV_NULL) return;

    if (g_GlobalStream != SV_NULL) {
        SvDetachSpeakerFromStream(g_GlobalStream, playerid);
    }
    if (g_PlayerChannel[playerid] != SV_NULL) {
        SvDetachSpeakerFromStream(g_PlayerChannel[playerid], playerid);
    }

    // 先自定义、后公共：单 speaker 插件下至少公共频道能说话
    if (g_PlayerChannel[playerid] != SV_NULL) {
        SvAttachSpeakerToStream(g_PlayerChannel[playerid], playerid);
    }
    if (g_PlayerVoiceEnabled[playerid] && g_GlobalStream != SV_NULL) {
        SvAttachSpeakerToStream(g_GlobalStream, playerid);
    }
}

public SV_VOID:OnPlayerActivationKeyRelease(SV_UINT:playerid, SV_UINT:keyid) {
    if (!IsVoiceKey(keyid)) return;
    if (SvGetVersion(playerid) == SV_NULL) return;

    if (g_GlobalStream != SV_NULL) {
        SvDetachSpeakerFromStream(g_GlobalStream, playerid);
    }
    if (g_PlayerChannel[playerid] != SV_NULL) {
        SvDetachSpeakerFromStream(g_PlayerChannel[playerid], playerid);
    }
}

public OnPlayerCommandText(playerid, cmdtext[]) {
    if (strcmp(cmdtext, "/voice", true) == 0) {
        ShowVoiceMainDialog(playerid);
        return 1;
    }
    return 0;
}

// ==================== Dialog ====================

stock ShowVoiceMainDialog(playerid) {
    new title[192];
    new currentName[MAX_VOICE_CHANNEL_NAME] = "公共频道";
    new bool:hasCustom = (g_PlayerChannel[playerid] != SV_NULL);
    new count = 0;

    if (hasCustom) {
        new idx = FindChannelByHandle(g_PlayerChannel[playerid]);
        if (idx != -1) {
            format(currentName, sizeof(currentName), "公共 + %s", g_VoiceChannels[idx][vcName]);
            count = CountPlayersInStream(g_PlayerChannel[playerid]);
        }
        format(title, sizeof(title), "语音管理 (当前: %s, %d人)", currentName, count);
        ShowPlayerDialog(playerid, DIALOG_VOICE_MAIN, DIALOG_STYLE_LIST,
            title, "创建频道\n加入频道\n查看当前频道玩家\n公共频道设置", "选择", "取消");
    } else {
        format(title, sizeof(title), "语音管理 (当前: %s)", currentName);
        ShowPlayerDialog(playerid, DIALOG_VOICE_MAIN, DIALOG_STYLE_LIST,
            title, "创建频道\n加入频道\n公共频道设置", "选择", "取消");
    }
}

stock ShowJoinChannelDialog(playerid) {
    new string[2048];
    string[0] = EOS;

    new bool:hasExit = (g_PlayerChannel[playerid] != SV_NULL);
    if (hasExit) strcat(string, "退出当前自定义频道");

    for (new i = 0; i < g_VoiceChannelCount; i++) {
        if (string[0] != EOS) strcat(string, "\n");
        new line[96];
        if (g_VoiceChannels[i][vcHandle] == g_PlayerChannel[playerid]) {
            format(line, sizeof(line), "%s (当前, %d人)",
                g_VoiceChannels[i][vcName], CountPlayersInStream(g_VoiceChannels[i][vcHandle]));
        } else {
            format(line, sizeof(line), "%s (%d人)",
                g_VoiceChannels[i][vcName], CountPlayersInStream(g_VoiceChannels[i][vcHandle]));
        }
        strcat(string, line);
    }
    if (string[0] == EOS) strcat(string, "(暂无可用频道)");

    ShowPlayerDialog(playerid, DIALOG_VOICE_JOIN, DIALOG_STYLE_LIST,
        "加入频道", string, "加入", "取消");
}

stock ShowPublicChannelSettings(playerid) {
    new string[256];
    format(string, sizeof(string),
        "听筒（收听别人说话）: %s\n麦克风（自己说话）: %s",
        g_PlayerSoundEnabled[playerid] ? "开" : "关",
        g_PlayerVoiceEnabled[playerid] ? "开" : "关");
    ShowPlayerDialog(playerid, DIALOG_VOICE_PUBLIC, DIALOG_STYLE_LIST,
        "公共频道设置", string, "切换", "取消");

    if (g_PlayerChannel[playerid] != SV_NULL) {
        SendClientMessage(playerid, COLOR_YELLOW,
            "[语音] 你在自定义频道中：麦克风开 = 双推；麦克风关 = 只推自定义频道。");
    }
}

stock ShowChannelMembers(playerid) {
    if (g_PlayerChannel[playerid] == SV_NULL) {
        SendClientMessage(playerid, COLOR_YELLOW, "[语音] 你当前在公共频道，无法查看自定义频道成员。");
        return;
    }

    new idx = FindChannelByHandle(g_PlayerChannel[playerid]);
    if (idx == -1) {
        SendClientMessage(playerid, COLOR_YELLOW, "[语音] 频道不存在。");
        return;
    }

    new string[1024];
    string[0] = EOS;
    new count = 0;
    new name[MAX_PLAYER_NAME];

    for (new i = 0; i < MAX_PLAYERS; i++) {
        if (i == playerid) continue;
        if (!IsPlayerConnected(i) || IsPlayerNPC(i)) continue;
        if (g_PlayerChannel[i] != g_PlayerChannel[playerid]) continue;

        GetPlayerName(i, name, sizeof(name));
        new line[96];
        format(line, sizeof(line), "%s (ID: %d)%s\n",
            name, i,
            g_PlayerVoiceEnabled[i] ? "" : " [禁麦]");
        strcat(string, line);
        count++;
    }
    if (count == 0) strcat(string, "(暂无其他玩家)");

    new title[128];
    format(title, sizeof(title), "频道成员 - %s (你 + %d人)",
        g_VoiceChannels[idx][vcName], count);

    ShowPlayerDialog(playerid, DIALOG_VOICE_MEMBERS, DIALOG_STYLE_LIST,
        title, string, "关闭", "");
}

public OnDialogResponse(playerid, dialogid, response, listitem, inputtext[]) {
    switch (dialogid) {
        case DIALOG_VOICE_MAIN: {
            if (!response) return 1;
            new bool:hasCustom = (g_PlayerChannel[playerid] != SV_NULL);
            switch (listitem) {
                case 0: {
                    ShowPlayerDialog(playerid, DIALOG_VOICE_CREATE, DIALOG_STYLE_INPUT,
                        "创建频道", "请输入频道名称:", "创建", "取消");
                }
                case 1: ShowJoinChannelDialog(playerid);
                case 2: {
                    if (hasCustom) ShowChannelMembers(playerid);
                    else ShowPublicChannelSettings(playerid);
                }
                case 3: {
                    if (hasCustom) ShowPublicChannelSettings(playerid);
                }
            }
            return 1;
        }

        case DIALOG_VOICE_CREATE: {
            if (!response) return 1;
            if (strlen(inputtext) == 0) {
                SendClientMessage(playerid, -1, "[语音] 频道名称不能为空");
                return 1;
            }
            if (g_VoiceChannelCount >= MAX_VOICE_CHANNELS) {
                SendClientMessage(playerid, -1, "[语音] 频道数量已达上限");
                return 1;
            }
            if (SvGetVersion(playerid) == SV_NULL) {
                SendClientMessage(playerid, -1, "[语音] 未安装 SampVoice 插件，无法创建频道");
                return 1;
            }

            new chanName[MAX_VOICE_CHANNEL_NAME];
            strmid(chanName, inputtext, 0, MAX_VOICE_CHANNEL_NAME - 1, MAX_VOICE_CHANNEL_NAME);

            if (FindChannelByName(chanName) != -1) {
                SendClientMessage(playerid, COLOR_YELLOW,
                    "[语音] 该频道名称已存在，请换一个。");
                return 1;
            }

            new SV_GSTREAM:stream = SvCreateGStream(0xFFFFFFFF, chanName);
            if (stream == SV_NULL) {
                SendClientMessage(playerid, -1, "[语音] 创建频道失败");
                return 1;
            }

            g_VoiceChannels[g_VoiceChannelCount][vcHandle] = stream;
            format(g_VoiceChannels[g_VoiceChannelCount][vcName], MAX_VOICE_CHANNEL_NAME, "%s", chanName);
            g_VoiceChannelCount++;

            JoinCustomChannel(playerid, stream);

            new msg[128];
            format(msg, sizeof(msg), "[语音] 已创建并加入频道: %s", chanName);
            SendClientMessage(playerid, COLOR_GREEN, msg);
            return 1;
        }

        case DIALOG_VOICE_JOIN: {
            if (!response) return 1;

            new bool:hasExit = (g_PlayerChannel[playerid] != SV_NULL);
            new idx = listitem;

            if (hasExit) {
                if (idx == 0) {
                    JoinCustomChannel(playerid, SV_NULL);
                    SendClientMessage(playerid, COLOR_GREEN, "[语音] 已退出当前自定义频道。");
                    return 1;
                }
                idx--;
            }

            if (idx >= 0 && idx < g_VoiceChannelCount) {
                if (g_VoiceChannels[idx][vcHandle] == g_PlayerChannel[playerid]) {
                    SendClientMessage(playerid, COLOR_YELLOW, "[语音] 你已在该频道中。");
                    return 1;
                }
                JoinCustomChannel(playerid, g_VoiceChannels[idx][vcHandle]);
                new msg[128];
                format(msg, sizeof(msg), "[语音] 已加入频道: %s", g_VoiceChannels[idx][vcName]);
                SendClientMessage(playerid, COLOR_GREEN, msg);
            }
            return 1;
        }

        case DIALOG_VOICE_PUBLIC: {
            if (!response) return 1;
            switch (listitem) {
                case 0: {
                    g_PlayerSoundEnabled[playerid] = !g_PlayerSoundEnabled[playerid];
                    if (g_GlobalStream != SV_NULL && SvGetVersion(playerid) != SV_NULL) {
                        if (g_PlayerSoundEnabled[playerid]) {
                            SvAttachListenerToStream(g_GlobalStream, playerid);
                        } else {
                            SvDetachListenerFromStream(g_GlobalStream, playerid);
                        }
                    }
                    new msg[64];
                    format(msg, sizeof(msg), "[语音] 公共频道听筒: %s",
                        g_PlayerSoundEnabled[playerid] ? "开" : "关");
                    SendClientMessage(playerid, 0xFFFFFFFF, msg);
                    ShowPublicChannelSettings(playerid);
                }
                case 1: {
                    g_PlayerVoiceEnabled[playerid] = !g_PlayerVoiceEnabled[playerid];
                    if (!g_PlayerVoiceEnabled[playerid]
                        && g_GlobalStream != SV_NULL
                        && SvGetVersion(playerid) != SV_NULL) {
                        SvDetachSpeakerFromStream(g_GlobalStream, playerid);
                    }
                    new msg[96];
                    if (g_PlayerChannel[playerid] != SV_NULL) {
                        format(msg, sizeof(msg),
                            "[语音] 公共频道麦克风: %s（当前在自定义频道：%s）",
                            g_PlayerVoiceEnabled[playerid] ? "开" : "关",
                            g_PlayerVoiceEnabled[playerid] ? "双推" : "只推自定义频道");
                    } else {
                        format(msg, sizeof(msg), "[语音] 公共频道麦克风: %s",
                            g_PlayerVoiceEnabled[playerid] ? "开" : "关");
                    }
                    SendClientMessage(playerid, 0xFFFFFFFF, msg);
                    ShowPublicChannelSettings(playerid);
                }
            }
            return 1;
        }

        case DIALOG_VOICE_MEMBERS: return 1;
    }
    return 0;
}