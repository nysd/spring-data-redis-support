package org.springframework.data.redis.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Created by yoshidan on 2015/09/28.
 */
public class RedisState<K,V> implements InitializingBean, DisposableBean{

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisState.class);

    /** redis template . */
    private final RedisTemplate<K,V> redisTemplate;

    /** interval msec */
    private int checkInterval = 5000;

    /** alive. */
    private boolean alive = true;

    /** lister thread. */
    private Listener listener = new Listener();

    /**
     * Constructor.
     *
     * @param redisTemplate to set
     */
    public RedisState(RedisTemplate<K, V> redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    /**
     * @return RedisTemplate
     */
    public RedisTemplate<K,V> getRedisTemplate(){
        return this.redisTemplate;
    }

    /**
     * @param checkInterval to set
     */
    public void setCheckInterval(int checkInterval) {
        this.checkInterval = checkInterval;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        listener.start();
    }

    @Override
    public void destroy() throws Exception {
        listener.running = false;
    }

    /**
     * @return true alive / false dead
     */
    public boolean isAlive() {
        return alive;
    }

    private class Listener extends Thread {

        private boolean running = true;

        @Override
        public void run() {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            while(running) {
                RedisConnection connection = RedisConnectionUtils.getConnection(factory);
                try {
                    Properties prop = connection.info("replication");
                    String status = prop.getProperty("master_sync_in_progress");
                    alive = !"1".equals(status);
                    if(!alive){
                        if(LOGGER.isDebugEnabled()){
                            LOGGER.error("slave full resync from master :" + prop);
                        }else {
                            LOGGER.error("slave full resync from master");
                        }
                    }
                } catch (Throwable t) {
                    if(LOGGER.isDebugEnabled()){
                        LOGGER.error("redis connection error : " + t.getMessage(),t);
                    }else{
                        LOGGER.error("redis connection error : " + t.getMessage());
                    }
                    alive = false;
                } finally {
                    RedisConnectionUtils.releaseConnection(connection, factory);
                    sleep();
                }
            }
        }


        /**
         * Sleep
         */
        private void sleep(){
            try {
                Thread.sleep(checkInterval);
            }catch(InterruptedException e){
                LOGGER.warn(e.getMessage(),e);
            }
        }
    }



}
