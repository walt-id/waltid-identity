// Temporary observation: distinguish main returning from incomplete console delivery.
#include <execinfo.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

extern int main(int argc, char **argv);
static int marker = -1;
static int main_returned = 0;

static void on_exit(void) {
    dprintf(marker, "atexit main_returned=%d\n", main_returned);
    if (!main_returned) {
        void *frames[24];
        int count = backtrace(frames, 24);
        backtrace_symbols_fd(frames, count, marker);
    }
    close(marker);
}

int keychainObservedMain(int argc, char **argv) {
    const char *path = getenv("WAL1585_KEYCHAIN_MARKER");
    if (!path || (marker = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0600)) < 0) return 125;
    dprintf(marker, "entered pid=%d\n", getpid());
    if (atexit(on_exit) != 0) return 125;
    int result = main(argc, argv);
    main_returned = 1;
    dprintf(marker, "main_returned result=%d\n", result);
    return result;
}
