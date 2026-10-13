// ============================================================
//  AirportSpawn 游戏模式（open.mp 服务器）
//  功能：
//    1) 玩家进入服务器后出生在机场（LS 国际机场停车场）
//    2) /c <车辆id> 刷车并直接上车，例：/c 562
//  编译：pawncc AirportSpawn.pwn （App 文件页编译或本机 pawncc）
//  编码：游戏内消息已用英文（避免 0.3.7 客户端编码问题）；
//        若需中文消息，源码请按 GBK 编码保存后编译。
// ============================================================

#include <a_samp>

#define COLOR_YELLOW 0xFFFFAA00
#define COLOR_GREEN  0xFF00FF00
#define COLOR_RED    0xFFFF0000

// LS 国际机场出生点（机场停车场，开阔安全）
#define AIRPORT_X 2494.0
#define AIRPORT_Y -1668.0
#define AIRPORT_Z 13.3
#define AIRPORT_A 0.0

main() {}

public OnGameModeInit()
{
	SetGameModeText("Airport Spawn");
	AddPlayerClass(0, AIRPORT_X, AIRPORT_Y, AIRPORT_Z, AIRPORT_A, 0, 0, 0, 0, 0, 0);
	return 1;
}

public OnPlayerRequestClass(playerid, classid)
{
	// 自动出生（可靠方案）：先进「观察者」绕过出生画面，稍后再强制出生
	if (IsPlayerConnected(playerid)) TogglePlayerSpectating(playerid, true);
	SetTimerEx("ForcedSpawn", 60, false, "i", playerid);
	return true;
}

forward ForcedSpawn(playerid);
public ForcedSpawn(playerid)
{
	if (!IsPlayerConnected(playerid)) return;
	TogglePlayerSpectating(playerid, false);
	SpawnPlayer(playerid);
}

public OnPlayerSpawn(playerid)
{
	// 强制出生在机场
	SetPlayerPos(playerid, AIRPORT_X, AIRPORT_Y, AIRPORT_Z);
	SetPlayerFacingAngle(playerid, AIRPORT_A);
	SetCameraBehindPlayer(playerid);
	SendClientMessage(playerid, COLOR_YELLOW, "Type /c <vehicle id> to spawn a car. Example: /c 562");
	return 1;
}

public OnPlayerCommandText(playerid, cmdtext[])
{
	new cmd[20], idx;
	cmd = strtok(cmdtext, idx);

	if (strcmp(cmd, "/c", true) == 0)
	{
		new param[20];
		param = strtok(cmdtext, idx);
		new carid = strval(param);

		if (carid < 1 || carid > 611)
		{
			SendClientMessage(playerid, COLOR_RED, "Invalid vehicle id. Use 1-611.");
			return 1;
		}

		new Float:x, Float:y, Float:z, Float:a;
		GetPlayerPos(playerid, x, y, z);
		GetPlayerFacingAngle(playerid, a);

		// 在玩家前方 5 米处生成车辆并让玩家直接上车
		new Float:dx = 5.0 * floatsin(-a, degrees);
		new Float:dy = 5.0 * floatcos(-a, degrees);
		new veh = CreateVehicle(carid, x + dx, y + dy, z + 0.5, a, -1, -1, -1);
		if (veh != INVALID_VEHICLE_ID)
		{
			PutPlayerInVehicle(playerid, veh, 0);
			SendClientMessage(playerid, COLOR_GREEN, "Vehicle spawned. You are now driving it.");
		}
		else
		{
			SendClientMessage(playerid, COLOR_RED, "Failed to spawn vehicle (invalid id?).");
		}
		return 1;
	}

	return 0;
}

// 经典参数分割函数（a_samp 无内置）
strtok(const string[], &index)
{
	new length = strlen(string);
	while ((index < length) && (string[index] <= ' ')) index++;

	new offset = index;
	new result[20];

	while ((index < length) && (string[index] > ' ') && ((index - offset) < (sizeof(result) - 1)))
	{
		result[index - offset] = string[index];
		index++;
	}
	result[index - offset] = EOS;
	return result;
}
