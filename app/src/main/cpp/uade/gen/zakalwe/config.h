#ifndef _Z_CONFIG_H_
#define _Z_CONFIG_H_

#if defined(__ANDROID__) && __ANDROID_API__ < 29
#define _Z_NEED_REALLOCARRAY 1
#endif
#define _Z_SYSCALL_SELECT 1
#define _Z_HAS_MKDTEMP 1

#endif
