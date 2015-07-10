package qty.aws.lambda;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ApplicationExecutor {
    private static Log logger = LogFactory.getLog(ApplicationExecutor.class);
    private ProcessBuilder builder;
    private Process process;
    protected String[] arguments = new String[0];
    final Vector<Integer> exitValue = new Vector<Integer>();

    public ApplicationExecutor(String app, String... args) throws FileNotFoundException {
        this(false, app, args);
    }

    public ApplicationExecutor(boolean enableNice, String app, String... args) throws FileNotFoundException {
        String absPathApp = null;
        File file = new File(app);
        if (!file.exists()) {
            throw new FileNotFoundException(app);
        }
        absPathApp = file.getAbsolutePath();
        List<String> cmd = new ArrayList<String>();

        cmd.add(absPathApp);
        if (args != null && args.length != 0) {
            cmd.addAll(Arrays.asList(args));
            this.arguments = args;
        }

        logger.info(cmd);
        builder = new ProcessBuilder(cmd);
        logger.debug("prepare app: " + app);
    }

    public void setWorkingDirectory(File directory) {
        if (builder != null) {
            builder.directory(directory);
        }
    }

    public String execute(final long timeout) throws IOException {
        return execute(timeout, 0);
    }

    public String execute(final long timeout, final long maxOutputSize) throws IOException {
        builder.redirectErrorStream(true);
        process = builder.start();

        try {
            return doExecute(timeout, maxOutputSize);
        } finally {
            try {
                if (process != null) {
                    process.destroy();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String doExecute(final long timeout, final long maxOutputSize) throws IOException {
        final Thread processWatcher = newProcessWaitingThread();
        final InputStream stdout = process.getInputStream();
        final InputStream stderr = process.getErrorStream();
        final ByteArrayOutputStream baout = new ByteArrayOutputStream();
        final byte[] buf = new byte[1024];

        startProcessOutputReadThread(maxOutputSize, processWatcher, stdout, stderr, baout, buf);
        startProcessAndWaitForExit(timeout, processWatcher);
        flushProcessOutput(stdout, baout, buf);
        return finishProcess(maxOutputSize, stdout, stderr, baout);
    }

    protected void startProcessOutputReadThread(final long maxOutputSize, final Thread processWatcher,
            final InputStream stdout, final InputStream stderr, final ByteArrayOutputStream baout, final byte[] buf) {
        new Thread() {
            public void run() {
                try {
                    while (processWatcher.isAlive()) {
                        readResponse(stderr, null, buf);
                        readResponse(stdout, baout, buf);
                        Thread.sleep(100);
                        if (maxOutputSize != 0 && baout.size() > maxOutputSize) {
                            break;
                        }
                    }
                } catch (IOException e) {
                } catch (InterruptedException e) {
                }
            }
        }.start();
    }

    protected Thread newProcessWaitingThread() {
        final Thread t = new Thread() {
            public void run() {
                while (true) {
                    if (ApplicationExecutor.this.process == null)
                        break;
                    try {
                        exitValue.add(ApplicationExecutor.this.process.exitValue());
                        return;
                    } catch (Exception e) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {
                        }
                    }
                }
            };
        };
        return t;
    }

    protected void flushProcessOutput(final InputStream stdout, final ByteArrayOutputStream baout, final byte[] buf)
            throws IOException {
        while (readResponse(stdout, baout, buf)) {
            ;
        }
    }

    protected void startProcessAndWaitForExit(final long timeout, final Thread processWatcher) {
        try {
            processWatcher.start();
            processWatcher.join(timeout);
        } catch (InterruptedException e) {
        }
    }

    protected String finishProcess(final long maxOutputSize, final InputStream stdout, final InputStream stderr,
            final ByteArrayOutputStream baout) {
        try {
            if (exitValue.isEmpty()) {
                process.destroy();
                if (maxOutputSize == 0) {
                    return null;
                } else {
                    return new String(baout.toByteArray());
                }
            }
        } catch (Exception e) {
        } finally {
            try {
                stdout.close();
            } catch (Exception ignored) {
            }
            try {
                stderr.close();
            } catch (Exception ignored) {
            }
        }

        return new String(baout.toByteArray());
    }

    protected boolean readResponse(final InputStream in, final ByteArrayOutputStream baout, byte[] buf)
            throws IOException {
        if (in.available() > 0) {
            int c = in.read(buf);
            if (baout != null) {
                baout.write(buf, 0, c);
            }
        }

        return in.available() > 0;
    }

    boolean isCompleted() {
        return !exitValue.isEmpty();
    }

}
