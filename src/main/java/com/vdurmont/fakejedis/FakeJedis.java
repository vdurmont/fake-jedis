package com.vdurmont.fakejedis;

import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.BitOP;
import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.Client;
import redis.clients.jedis.DebugParams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisMonitor;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.PipelineBlock;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.TransactionBlock;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.ZParams;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.util.Pool;
import redis.clients.util.Slowlog;

import java.util.*;

/**
 * Jedis wrapper that simulates the behaviour of redis
 *
 * @author Vincent DURMONT [vdurmont@gmail.com]
 */
public class FakeJedis extends Jedis {
    protected final Object LOCK;
    private boolean isMulti;
    private final Map<String, JedisObject> database;

    public FakeJedis() {
        super("");
        this.LOCK = new Object();
        this.database = new HashMap<>();
    }

    // //////////////////////
    // PUBLIC API
    // //////////////

    @Override public Boolean exists(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            return this.database.containsKey(key);
        }
    }

    @Override public Transaction multi() {
        synchronized (LOCK) {
            checkMulti();
            this.isMulti = true;
            return new FakeTransaction(this);
        }
    }

    @Override public Long del(String... keys) {
        synchronized (this.LOCK) {
            checkMulti();
            long sum = 0;
            for (String key : keys) {
                sum += this.del(key);
            }
            return sum;
        }
    }

    @Override public Long del(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisObject old = this.database.remove(key);
            return (long) (old == null ? 0 : 1);
        }
    }

    @Override public String set(String key, String value) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisString str = new JedisString(value);
            this.database.put(key, str);
            return "OK";
        }
    }

    @Override public String get(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisString str = this.get(JedisObjectType.STRING, key);
            return str == null ? null : str.value;
        }
    }

    @Override public Set<String> keys(String pattern) {
        synchronized (this.LOCK) {
            checkMulti();
            Set<String> keys = this.database.keySet();
            pattern = pattern.replace("*", "(.*)");
            Iterator<String> ite = keys.iterator();
            while (ite.hasNext()) {
                String key = ite.next();
                if (!key.matches(pattern)) {
                    ite.remove();
                }
            }
            return keys;
        }
    }

    @Override
    public void close() {
        // No-op
    }

    @Override public Long setnx(String key, String value) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisString obj = this.get(JedisObjectType.STRING, key);
            if (obj != null) {
                return 0l;
            }
            this.database.put(key, new JedisString(value));
            return 1l;
        }
    }

    // //////////////////////
    // PUBLIC API — LISTS
    // //////////////

    @Override public Long lpush(String key, String... strings) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisList jedisList = this.getOrCreate(JedisObjectType.LIST, key);
            for (String str : strings) {
                jedisList.list.addFirst(str);
            }
            return jedisList.size();
        }
    }

    @Override public String lpop(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisList jedisList = this.getOrCreate(JedisObjectType.LIST, key);
            return jedisList.list.pollFirst();
        }
    }

    @Override public Long llen(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisList jedisList = this.getOrCreate(JedisObjectType.LIST, key);
            return jedisList.size();
        }
    }

    @Override public List<String> lrange(String key, long start, long end) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisList jedisList = this.get(JedisObjectType.LIST, key);
            end++; // Because the end is included in redis
            if (jedisList == null) {
                return new ArrayList<>();
            } else {
                // Negative index means we start from the tail
                if (start < 0) {
                    start = jedisList.size() + start;
                }
                if (end < 1) {
                    end = jedisList.size() + end;
                }

                // If we have reversed index, return empty
                if (start > end) {
                    return new ArrayList<>();
                }

                // We are resilient to the IndexOutOfBounds errors
                start = Math.max(0, start);
                end = Math.min(jedisList.size(), end);

                return jedisList.list.subList((int) start, (int) end);
            }
        }
    }

    // //////////////////////
    // PUBLIC API — HASHES
    // //////////////

    @Override public Long hset(String key, String field, String value) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisHash jedisHash = this.getOrCreate(JedisObjectType.HASH, key);
            String old = jedisHash.hash.put(field, value);
            return (long) (old == null ? 1 : 0);
        }
    }

    @Override public String hget(String key, String field) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisHash jedisHash = this.get(JedisObjectType.HASH, key);
            if (jedisHash == null) {
                return null;
            }
            return jedisHash.hash.get(field);
        }
    }

    @Override public Long hincrBy(String key, String field, long value) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisHash jedisHash = this.getOrCreate(JedisObjectType.HASH, key);
            String old = jedisHash.hash.get(field);
            long newValue = value;
            if (old != null) {
                try {
                    newValue += Long.valueOf(old);
                } catch (NumberFormatException e) {
                    throw new JedisDataException("ERR hash value is not an integer");
                }
            }
            jedisHash.hash.put(field, String.valueOf(newValue));
            return newValue;
        }
    }

    @Override public Map<String, String> hgetAll(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisHash hash = this.get(JedisObjectType.HASH, key);
            Map<String, String> map = new HashMap<>();
            if (hash != null) {
                map.putAll(hash.hash);
            }
            return map;
        }
    }

    // //////////////////////
    // PUBLIC API — SETS
    // //////////////

    @Override public Long sadd(String key, String... members) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisSet jedisSet = this.getOrCreate(JedisObjectType.SET, key);
            long added = 0;
            for (String value : members) {
                if (jedisSet.set.add(value)) {
                    added++;
                }
            }

            return added;
        }
    }

    @Override public Set<String> smembers(String key) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisSet jedisSet = this.getOrCreate(JedisObjectType.SET, key);
            return jedisSet.set;
        }
    }

    @Override public Long srem(String key, String... members) {
        synchronized (this.LOCK) {
            checkMulti();
            JedisSet jedisSet = this.getOrCreate(JedisObjectType.SET, key);
            long removed = 0;
            for (String value : members) {
                if (jedisSet.set.remove(value)) {
                    removed++;
                }
            }

            return removed;
        }
    }

    // //////////////////////
    // PRIVATE TOOLS
    // //////////////

    private <T extends JedisObject> T get(JedisObjectType type, String key) {
        JedisObject object = this.database.get(key);
        if (object == null) {
            return null;
        } else if (object.type == type) {
            @SuppressWarnings("unchecked")
            T fetched = (T) object;
            return fetched;
        } else {
            throw new JedisDataException("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }

    private <T extends JedisObject> T getOrCreate(JedisObjectType type, String key) {
        T object = this.get(type, key);
        if (object == null) {
            object = type.newInstance();
            this.database.put(key, object);
        }
        return object;
    }

    private void checkMulti() {
        if (this.isMulti) {
            throw new JedisDataException("Cannot use Jedis when in Multi. Please use JedisTransaction instead.");
        }
    }

    protected void setMulti(boolean multi) {
        this.isMulti = multi;
    }

    // //////////////////////
    // MODEL
    // //////////////

    private static class JedisObject {
        public final JedisObjectType type;

        public JedisObject(JedisObjectType type) {
            this.type = type;
        }
    }

    private static class JedisList extends JedisObject {
        public final LinkedList<String> list;

        public JedisList() {
            super(JedisObjectType.LIST);
            this.list = new LinkedList<>();
        }

        public long size() {
            return (long) this.list.size();
        }
    }

    private static class JedisHash extends JedisObject {
        public final Map<String, String> hash;

        public JedisHash() {
            super(JedisObjectType.HASH);
            this.hash = new HashMap<>();
        }
    }

    private static class JedisSet extends JedisObject {
        public final Set<String> set;

        public JedisSet() {
            super(JedisObjectType.SET);
            this.set = new HashSet<>();
        }
    }

    private static class JedisString extends JedisObject {
        public final String value;

        public JedisString(String value) {
            super(JedisObjectType.STRING);
            this.value = value;
        }
    }

    private static enum JedisObjectType {
        LIST(JedisList.class),
        HASH(JedisHash.class),
        SET(JedisSet.class),
        STRING(JedisString.class);

        private final Class cls;

        private <T extends JedisObject> JedisObjectType(Class<T> cls) {
            this.cls = cls;
        }

        private <T extends JedisObject> T newInstance() {
            try {
                @SuppressWarnings("unchecked")
                T instance = (T) this.cls.newInstance();
                return instance;
            } catch (InstantiationException | IllegalAccessException e) {
                throw new FakeJedisException("Unable to create an instance of " + this.cls, e);
            }
        }
    }

    // //////////////////////
    // NOT IMPLEMENTED
    // //////////////

    @Override public String set(String key, String value, String nxxx, String expx, long time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String type(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String randomKey() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String rename(String oldkey, String newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long renamenx(String oldkey, String newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long expire(String key, int seconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long expireAt(String key, long unixTime) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long ttl(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long move(String key, int dbIndex) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String getSet(String key, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> mget(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String setex(String key, int seconds, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String mset(String... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long msetnx(String... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long decrBy(String key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long decr(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long incrBy(String key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double incrByFloat(String key, double value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long incr(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long append(String key, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String substr(String key, int start, int end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hsetnx(String key, String field, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String hmset(String key, Map<String, String> hash) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> hmget(String key, String... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double hincrByFloat(String key, String field, double value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean hexists(String key, String field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hdel(String key, String... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hlen(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> hkeys(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> hvals(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long rpush(String key, String... strings) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String ltrim(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String lindex(String key, long index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String lset(String key, long index, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lrem(String key, long count, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String rpop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String rpoplpush(String srckey, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String spop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long smove(String srckey, String dstkey, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long scard(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean sismember(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> sinter(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sinterstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> sunion(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sunionstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> sdiff(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sdiffstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String srandmember(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> srandmember(String key, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zadd(String key, double score, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zadd(String key, Map<String, Double> scoreMembers) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrange(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrem(String key, String... members) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double zincrby(String key, double score, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrank(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrevrank(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrevrange(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeWithScores(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeWithScores(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcard(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double zscore(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String watch(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> sort(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> sort(String key, SortingParams sortingParameters) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> blpop(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> blpop(String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> brpop(String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> blpop(String arg) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> brpop(String arg) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sort(String key, SortingParams sortingParameters, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sort(String key, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> brpop(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcount(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcount(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByScore(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByScore(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByScore(String key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByScore(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrevrangeByScore(String key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrevrangeByScore(String key, String max, String min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrevrangeByScore(String key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrevrangeByScore(String key, String max, String min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(String key, String max, String min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByRank(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByScore(String key, double start, double end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByScore(String key, String start, String end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zunionstore(String dstkey, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zunionstore(String dstkey, ZParams params, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zinterstore(String dstkey, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zinterstore(String dstkey, ZParams params, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zlexcount(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByLex(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<String> zrangeByLex(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByLex(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long strlen(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lpushx(String key, String... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long persist(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long rpushx(String key, String... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String echo(String string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long linsert(String key, BinaryClient.LIST_POSITION where, String pivot, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String brpoplpush(String source, String destination, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean setbit(String key, long offset, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean setbit(String key, long offset, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean getbit(String key, long offset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long setrange(String key, long offset, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String getrange(String key, long startOffset, long endOffset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitpos(String key, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitpos(String key, boolean value, BitPosParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> configGet(String pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String configSet(String parameter, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(String script, int keyCount, String... params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void subscribe(JedisPubSub jedisPubSub, String... channels) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long publish(String channel, String message) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void psubscribe(JedisPubSub jedisPubSub, String... patterns) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(String script, List<String> keys, List<String> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(String script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(String script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(String sha1, List<String> keys, List<String> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(String sha1, int keyCount, String... params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean scriptExists(String sha1) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Boolean> scriptExists(String... sha1) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String scriptLoad(String script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Slowlog> slowlogGet() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Slowlog> slowlogGet(long entries) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long objectRefcount(String string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String objectEncoding(String string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long objectIdletime(String string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitcount(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitcount(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitop(BitOP op, String destKey, String... srcKeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Map<String, String>> sentinelMasters() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> sentinelGetMasterAddrByName(String masterName) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sentinelReset(String pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Map<String, String>> sentinelSlaves(String masterName) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String sentinelFailover(String masterName) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String sentinelMonitor(String masterName, String ip, int port, int quorum) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String sentinelRemove(String masterName) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String sentinelSet(String masterName, Map<String, String> parameterMap) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] dump(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String restore(String key, int ttl, byte[] serializedValue) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpire(String key, int milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpire(String key, long milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpireAt(String key, long millisecondsTimestamp) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pttl(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String psetex(String key, int milliseconds, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(String key, String value, String nxxx) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(String key, String value, String nxxx, String expx, int time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientKill(String client) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientSetname(String name) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String migrate(String host, int port, String key, int destinationDb, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> scan(int cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> scan(int cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<String, String>> hscan(String key, int cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<String, String>> hscan(String key, int cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> sscan(String key, int cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> sscan(String key, int cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(String key, int cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(String key, int cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> scan(String cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> scan(String cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> sscan(String key, String cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(String key, String cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterNodes() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterMeet(String ip, int port) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterReset(JedisCluster.Reset resetType) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterAddSlots(int... slots) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterDelSlots(int... slots) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterInfo() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> clusterGetKeysInSlot(int slot, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterSetSlotNode(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterSetSlotMigrating(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterSetSlotImporting(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterSetSlotStable(int slot) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterForget(String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterFlushSlots() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long clusterKeySlot(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long clusterCountKeysInSlot(int slot) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterSaveConfig() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterReplicate(String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> clusterSlaves(String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clusterFailover() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Object> clusterSlots() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String asking() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> pubsubChannels(String pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pubsubNumPat() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Map<String, String> pubsubNumSub(String... channels) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void setDataSource(Pool<Jedis> jedisPool) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pfadd(String key, String... elements) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public long pfcount(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public long pfcount(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String pfmerge(String destkey, String... sourcekeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> blpop(int timeout, String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> brpop(int timeout, String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String ping() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, long time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] get(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String quit() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean exists(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long del(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long del(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String type(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String flushDB() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> keys(byte[] pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] randomBinaryKey() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String rename(byte[] oldkey, byte[] newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long renamenx(byte[] oldkey, byte[] newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long dbSize() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long expire(byte[] key, int seconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long expireAt(byte[] key, long unixTime) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long ttl(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String select(int index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long move(byte[] key, int dbIndex) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String flushAll() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] getSet(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> mget(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long setnx(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String setex(byte[] key, int seconds, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String mset(byte[]... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long msetnx(byte[]... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long decrBy(byte[] key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long decr(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long incrBy(byte[] key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double incrByFloat(byte[] key, double integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long incr(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long append(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] substr(byte[] key, int start, int end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hset(byte[] key, byte[] field, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] hget(byte[] key, byte[] field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hsetnx(byte[] key, byte[] field, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String hmset(byte[] key, Map<byte[], byte[]> hash) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> hmget(byte[] key, byte[]... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hincrBy(byte[] key, byte[] field, long value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double hincrByFloat(byte[] key, byte[] field, double value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean hexists(byte[] key, byte[] field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hdel(byte[] key, byte[]... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long hlen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> hkeys(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> hvals(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Map<byte[], byte[]> hgetAll(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long rpush(byte[] key, byte[]... strings) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lpush(byte[] key, byte[]... strings) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long llen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> lrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String ltrim(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] lindex(byte[] key, long index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String lset(byte[] key, long index, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lrem(byte[] key, long count, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] lpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] rpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] rpoplpush(byte[] srckey, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sadd(byte[] key, byte[]... members) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> smembers(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long srem(byte[] key, byte[]... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] spop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long smove(byte[] srckey, byte[] dstkey, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long scard(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean sismember(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> sinter(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sinterstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> sunion(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sunionstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> sdiff(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sdiffstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] srandmember(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> srandmember(byte[] key, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zadd(byte[] key, double score, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zadd(byte[] key, Map<byte[], Double> scoreMembers) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrem(byte[] key, byte[]... members) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double zincrby(byte[] key, double score, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrank(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zrevrank(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrevrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeWithScores(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeWithScores(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcard(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Double zscore(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Object> multi(TransactionBlock jedisTransaction) {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected void checkIsInMulti() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void connect() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void disconnect() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void resetState() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String watch(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String unwatch() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> sort(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> sort(byte[] key, SortingParams sortingParameters) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> blpop(int timeout, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sort(byte[] key, SortingParams sortingParameters, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long sort(byte[] key, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> brpop(int timeout, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> blpop(byte[] arg) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> brpop(byte[] arg) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> blpop(byte[]... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> brpop(byte[]... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String auth(String password) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Object> pipelined(PipelineBlock jedisPipeline) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Pipeline pipelined() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcount(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zcount(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByScore(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<Tuple> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByRank(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByScore(byte[] key, double start, double end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByScore(byte[] key, byte[] start, byte[] end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zunionstore(byte[] dstkey, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zinterstore(byte[] dstkey, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zlexcount(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Set<byte[]> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long zremrangeByLex(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String save() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String bgsave() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String bgrewriteaof() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lastsave() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String shutdown() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String info() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String info(String section) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void monitor(JedisMonitor jedisMonitor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String slaveof(String host, int port) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String slaveofNoOne() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> configGet(byte[] pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String configResetStat() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] configSet(byte[] parameter, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public boolean isConnected() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long strlen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void sync() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long lpushx(byte[] key, byte[]... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long persist(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long rpushx(byte[] key, byte[]... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] echo(byte[] string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long linsert(byte[] key, BinaryClient.LIST_POSITION where, byte[] pivot, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String debug(DebugParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Client getClient() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] brpoplpush(byte[] source, byte[] destination, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean setbit(byte[] key, long offset, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean setbit(byte[] key, long offset, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Boolean getbit(byte[] key, long offset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitpos(byte[] key, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitpos(byte[] key, boolean value, BitPosParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long setrange(byte[] key, long offset, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] getrange(byte[] key, long startOffset, long endOffset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long publish(byte[] channel, byte[] message) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void subscribe(BinaryJedisPubSub jedisPubSub, byte[]... channels) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public void psubscribe(BinaryJedisPubSub jedisPubSub, byte[]... patterns) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long getDB() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(byte[] script, byte[] keyCount, byte[]... params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(byte[] script, int keyCount, byte[]... params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object eval(byte[] script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(byte[] sha1) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Object evalsha(byte[] sha1, int keyCount, byte[]... params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String scriptFlush() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Long> scriptExists(byte[]... sha1) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] scriptLoad(byte[] script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String scriptKill() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String slowlogReset() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long slowlogLen() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> slowlogGetBinary() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<byte[]> slowlogGetBinary(long entries) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long objectRefcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] objectEncoding(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long objectIdletime(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitcount(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public byte[] dump(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String restore(byte[] key, int ttl, byte[] serializedValue) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpire(byte[] key, int milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpire(byte[] key, long milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pexpireAt(byte[] key, long millisecondsTimestamp) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pttl(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String psetex(byte[] key, int milliseconds, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(byte[] key, byte[] value, byte[] nxxx) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, int time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientKill(byte[] client) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientGetname() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientList() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String clientSetname(byte[] name) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<String> time() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String migrate(byte[] host, int port, byte[] key, int destinationDb, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long waitReplicas(int replicas, long timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pfadd(byte[] key, byte[]... elements) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public long pfcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String pfmerge(byte[] destkey, byte[]... sourcekeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Long pfcount(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<byte[]> scan(byte[] cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<byte[]> scan(byte[] cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Map.Entry<byte[], byte[]>> hscan(byte[] key, byte[] cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<byte[]> sscan(byte[] key, byte[] cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<byte[]> sscan(byte[] key, byte[] cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(byte[] key, byte[] cursor) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public ScanResult<Tuple> zscan(byte[] key, byte[] cursor, ScanParams params) {
        throw new FakeJedisNotImplementedException();
    }
}
