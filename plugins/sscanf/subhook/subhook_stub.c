/* subhook_stub.c
 *
 * ARM64 (open.mp Android) stub for subhook.
 *
 * subhook 本身只支持 x86/x64（subhook_x86.c 是 x86 汇编），arm64 无实现。
 * sscanf 的 SA-MP 插件方式用 subhook 拦截 amx_Register，但 open.mp Android
 * 用【组件方式】(COMPONENT_ENTRY_POINT / SScanFComponent) 加载 sscanf，
 * 组件方式不调用 subhook，因此这里提供空实现仅用于让插件方式的符号链接通过。
 */

/* 不 include subhook.h：其架构检查只认 x86/x64，arm64 会 #error。
 * 这里自行定义 subhook_t（与 subhook.h 中 typedef struct subhook *subhook_t 一致）。 */

typedef struct subhook *subhook_t;
#define SUBHOOK_API
#define SUBHOOK_EXPORT extern

SUBHOOK_EXPORT subhook_t SUBHOOK_API subhook_new(void *src, void *dst)
{
	(void)src;
	(void)dst;
	return 0;
}

SUBHOOK_EXPORT void SUBHOOK_API subhook_free(subhook_t hook)
{
	(void)hook;
}

SUBHOOK_EXPORT void *SUBHOOK_API subhook_get_src(subhook_t hook)
{
	(void)hook;
	return 0;
}

SUBHOOK_EXPORT void *SUBHOOK_API subhook_get_dst(subhook_t hook)
{
	(void)hook;
	return 0;
}

SUBHOOK_EXPORT void *SUBHOOK_API subhook_get_trampoline(subhook_t hook)
{
	(void)hook;
	return 0;
}

SUBHOOK_EXPORT int SUBHOOK_API subhook_install(subhook_t hook)
{
	(void)hook;
	return -1;
}

SUBHOOK_EXPORT int SUBHOOK_API subhook_is_installed(subhook_t hook)
{
	(void)hook;
	return 0;
}

SUBHOOK_EXPORT int SUBHOOK_API subhook_remove(subhook_t hook)
{
	(void)hook;
	return 0;
}
