package cn.nolaurene.cms.service.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.worker.resp.supervisor.ProcessInfo;
import cn.nolaurene.cms.common.sandbox.worker.resp.supervisor.SupervisorActionResult;
import cn.nolaurene.cms.common.sandbox.worker.resp.supervisor.SupervisorTimeout;
import cn.nolaurene.cms.exception.manus.BadRequestException;
import cn.nolaurene.cms.exception.manus.ResourceNotFoundException;
import org.w3c.dom.Document;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description:
 */
public class SupervisorService {
    
    private static final Integer SERVICE_TIMEOUT_MINUTES = 10;
    
    private final String rpcUrl = "/tmp/supervisor.sock";
    private UnixStreamHTTPConnection server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean timeoutActive;
    private ScheduledFuture<?> shutdownTask;
    private LocalDateTime shutdownTime;

    public SupervisorService() {
        _connectRpc();

        timeoutActive = SERVICE_TIMEOUT_MINUTES != null;
        if (SERVICE_TIMEOUT_MINUTES != null) {
            shutdownTime = LocalDateTime.now().plusMinutes(SERVICE_TIMEOUT_MINUTES);
            _setupTimer(SERVICE_TIMEOUT_MINUTES);
        }
    }

    private void _connectRpc() {
        try {
            server = new UnixStreamHTTPConnection(rpcUrl);
            server.call("supervisor.getState");
        } catch (Exception e) {
            throw new ResourceNotFoundException("Cannot connect to Supervisord");
        }
    }

    private void _setupTimer(int minutes) {
        if (shutdownTask != null) {
            shutdownTask.cancel(false);
        }
        shutdownTask = scheduler.schedule(() -> {
            try {
                shutdown().join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, minutes * 60, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<ProcessInfo>> getAllProcesses() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = server.call("supervisor.getAllProcessInfo");
                // Parse document into ProcessInfo objects
                return new ArrayList<>();
            } catch (Exception e) {
                throw new BadRequestException("RPC call failed", e);
            }
        });
    }

    public CompletableFuture<SupervisorActionResult> stopAllServices() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                server.call("supervisor.stopAllProcesses");
                return new SupervisorActionResult("stopped", null, null, null, null);
            } catch (Exception e) {
                throw new BadRequestException("Failed to stop all services", e);
            }
        });
    }

    public CompletableFuture<SupervisorActionResult> shutdown() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                server.call("supervisor.shutdown");
                return new SupervisorActionResult("shutdown", null, null, null, null);
            } catch (Exception e) {
                throw new BadRequestException("Failed to shut down supervisord", e);
            }
        });
    }

    public CompletableFuture<SupervisorActionResult> restartAllServices() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                server.call("supervisor.stopAllProcesses");
                server.call("supervisor.startAllProcesses");
                return new SupervisorActionResult("restarted", null, null, null, null);
            } catch (Exception e) {
                throw new BadRequestException("Failed to restart services", e);
            }
        });
    }

    public CompletableFuture<SupervisorTimeout> activateTimeout(Integer minutes) {
        return CompletableFuture.supplyAsync(() -> {
            int timeoutMinutes = minutes != null ? minutes : SERVICE_TIMEOUT_MINUTES;
            if (timeoutMinutes == 0) {
                throw new BadRequestException("Timeout not specified, and system default is no timeout");
            }

            shutdownTime = LocalDateTime.now().plusMinutes(timeoutMinutes);
            _setupTimer(timeoutMinutes);
            timeoutActive = true;

            return new SupervisorTimeout("timeout_activated", true, shutdownTime.toString(), 0, timeoutMinutes);
        });
    }

    public CompletableFuture<SupervisorTimeout> extendTimeout(Integer minutes) {
        return CompletableFuture.supplyAsync(() -> {
            int timeoutMinutes = minutes != null ? minutes : SERVICE_TIMEOUT_MINUTES;
            if (timeoutMinutes == 0) {
                throw new BadRequestException("Timeout not specified, and system default is no timeout");
            }

            shutdownTime = LocalDateTime.now().plusMinutes(timeoutMinutes);
            _setupTimer(timeoutMinutes);

            return new SupervisorTimeout("timeout_extended", true, shutdownTime.toString(), 0, timeoutMinutes);
        });
    }

    public CompletableFuture<SupervisorTimeout> cancelTimeout() {
        return CompletableFuture.supplyAsync(() -> {
            if (!timeoutActive) {
                return new SupervisorTimeout("no_timeout_active", false, null, 0, 0);
            }

            if (shutdownTask != null) {
                shutdownTask.cancel(false);
                shutdownTask = null;
            }

            timeoutActive = false;
            shutdownTime = null;

            return new SupervisorTimeout("timeout_cancelled", false, null, 0, 0);
        });
    }

    public CompletableFuture<SupervisorTimeout> getTimeoutStatus() {
        return CompletableFuture.supplyAsync(() -> {
            if (!timeoutActive) {
                return new SupervisorTimeout(null, false, null, 0, 0);
            }

            long remainingSeconds = 0;
            if (shutdownTime != null) {
                Duration duration = Duration.between(LocalDateTime.now(), shutdownTime);
                remainingSeconds = Math.max(0, duration.getSeconds());
            }

            return new SupervisorTimeout(null, true, shutdownTime.toString(), remainingSeconds, SERVICE_TIMEOUT_MINUTES);
        });
    }
}
