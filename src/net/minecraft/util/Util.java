package net.minecraft.util;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.annotation.Nullable;

import carpet.CarpetSettings;
import carpet.helpers.ThrowableSuppression;
import org.apache.logging.log4j.Logger;

public class Util
{
    /**
     * Run a task and return the result, catching any execution exceptions and logging them to the specified logger
     */
    @Nullable
    public static <V> V runTask(FutureTask<V> task, Logger logger)
    {
        try
        {
            task.run();
            return task.get();
        }
        catch (ExecutionException executionexception)
        {
            // Update Suppression crash fixes CARPET-XCOM
            if(!CarpetSettings.updateSuppressionCrashFix || !(executionexception.getCause() instanceof ThrowableSuppression))
                logger.fatal("Error executing task", (Throwable)executionexception);
            net.minecraft.server.management.PlayerInteractionManager.playerMinedBlock = null;
        }
        catch (InterruptedException interruptedexception)
        {
            logger.fatal("Error executing task", (Throwable)interruptedexception);
        }

        return (V)null;
    }

    public static <T> T getLastElement(List<T> list)
    {
        return list.get(list.size() - 1);
    }
}