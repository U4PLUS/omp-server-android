/*
 *  sscanf 移植测试脚本 (sscanf_test.pwn)
 *  用于确认 sscanf 组件在 open.mp Android 上正常工作。
 *  放到 files/server/filterscripts/ 下，重启服务器后查看日志 [sscanf_test]。
 */
#include <open.mp>
#include <sscanf2>

// open.mp 的 printf 会输出到服务器控制台/日志（log.txt）

public OnFilterScriptInit()
{
	printf("[sscanf_test] ===== sscanf test begin =====");

	// 1. 整数
	new i;
	if (sscanf("123", "i", i)) printf("[sscanf_test] T1(int) FAIL");
	else printf("[sscanf_test] T1(int) OK -> i=%d", i);

	// 2. 负整数
	if (sscanf("-42", "i", i)) printf("[sscanf_test] T2(neg) FAIL");
	else printf("[sscanf_test] T2(neg) OK -> i=%d", i);

	// 3. 浮点
	new Float:f;
	if (sscanf("3.14", "f", f)) printf("[sscanf_test] T3(float) FAIL");
	else printf("[sscanf_test] T3(float) OK -> f=%.2f", f);

	// 4. 字符串
	new str[64];
	if (sscanf("hello world", "s[64]", str)) printf("[sscanf_test] T4(str) FAIL");
	else printf("[sscanf_test] T4(str) OK -> str=%s", str);

	// 5. 组合 整数+字符串
	new id, name[32];
	if (sscanf("42 player", "is[32]", id, name)) printf("[sscanf_test] T5(i+s) FAIL");
	else printf("[sscanf_test] T5(i+s) OK -> id=%d name=%s", id, name);

	// 6. 多个整数
	new a, b, c;
	if (sscanf("1 2 3", "iii", a, b, c)) printf("[sscanf_test] T6(iii) FAIL");
	else printf("[sscanf_test] T6(iii) OK -> %d %d %d", a, b, c);

	// 7. 浮点+整数
	new Float:ff, ni;
	if (sscanf("2.5 7", "fi", ff, ni)) printf("[sscanf_test] T7(f+i) FAIL");
	else printf("[sscanf_test] T7(f+i) OK -> f=%.2f i=%d", ff, ni);

	// 8. 十六进制
	if (sscanf("0xFF", "h", i)) printf("[sscanf_test] T8(hex) FAIL");
	else printf("[sscanf_test] T8(hex) OK -> i=%d (0xFF=255)", i);

	printf("[sscanf_test] ===== sscanf test done =====");
	return 1;
}

public OnPlayerConnect(playerid)
{
	// 用真实玩家名测试 u 格式解析（需要 sscanf 的玩家名表）
	new name[24];
	GetPlayerName(playerid, name, sizeof(name));
	new pid;
	if (sscanf(name, "u", pid)) printf("[sscanf_test] T9(u player) FAIL player=%s", name);
	else printf("[sscanf_test] T9(u player) OK -> %s -> pid=%d", name, pid);
	return 1;
}
