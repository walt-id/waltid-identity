#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

static int redirectRecoveryOutput(const char *path) {
    // Kotlin/Native writes to descriptors 1/2 directly. Darwin freopen can move
    // the FILE stream to another descriptor, capturing C output but losing Kotlin's.
    int output = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (output < 0) return -1;
    int result = (dup2(output, STDOUT_FILENO) < 0 || dup2(output, STDERR_FILENO) < 0) ? -1 : 0;
    if (output != STDOUT_FILENO && output != STDERR_FILENO) close(output);
    if (result < 0) return result;
    setbuf(stdout, NULL);
    setbuf(stderr, NULL);
    return 0;
}
