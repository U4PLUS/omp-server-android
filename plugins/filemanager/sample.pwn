#include <open.mp>
#include <filemanager>

public OnFilterScriptInit()
{
    // ---- 文件写入测试 ----
    new File:handle = f_open("fm_test.txt", "w");
    if (handle)
    {
        f_write(handle, "FileManager arm64 移植测试");
        f_close(handle);
        printf("[FM] 写入成功，test.txt 已创建");
    }
    else
    {
        printf("[FM] 打开失败");
    }

    // ---- 文件读取测试 ----
    new File:rh = f_open("fm_test.txt", "r");
    if (rh)
    {
        new buf[128];
        f_read(rh, buf, sizeof(buf));
        f_close(rh);
        printf("[FM] 读取内容: %s", buf);
    }

    // ---- 目录操作测试 ----
    if (!dir_exists("fm_test_dir"))
        dir_create("fm_test_dir");

    new File:wh = f_open("fm_test_dir/hello.txt", "w");
    if (wh)
    {
        f_write(wh, "hello from subdirectory");
        f_close(wh);
    }

    // ---- 目录遍历测试 ----
    new dir:dh = dir_open_sorted(".");
    if (dh)
    {
        new name[64], ftype, count;
        printf("[FM] 当前目录内容:");
        while (dir_list(dh, name, ftype, sizeof(name)))
        {
            printf("[FM]   %s (%s)", name, ftype == FM_DIR ? "DIR" : "FILE");
            count++;
        }
        dir_close(dh);
        printf("[FM] 共 %d 项", count);
    }

    return 1;
}

public OnFilterScriptExit()
{
    // 清理测试文件
    file_delete("fm_test.txt");
    file_delete("fm_test_dir/hello.txt");
    dir_delete("fm_test_dir");
    printf("[FM] 清理完成");
    return 1;
}
