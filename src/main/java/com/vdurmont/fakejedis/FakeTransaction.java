package com.vdurmont.fakejedis;

import redis.clients.jedis.BinaryClient;
import redis.clients.jedis.BitOP;
import redis.clients.jedis.BitPosParams;
import redis.clients.jedis.Builder;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.Client;
import redis.clients.jedis.Response;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.ZParams;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transaction wrapper that enables us to simulate redis transactions
 *
 * @author Vincent DURMONT [vdurmont@gmail.com]
 */
public class FakeTransaction extends Transaction {
    private final FakeJedis jedis;
    private final List<Action> actions;

    public FakeTransaction(FakeJedis fakeJedis) {
        this.jedis = fakeJedis;
        this.actions = new LinkedList<>();
    }

    // //////////////////////
    // INTROSPECTION
    // //////////////

    private static Method getMethod(String name, Class<?>... argsTypes) {
        try {
            return FakeJedis.class.getMethod(name, argsTypes);
        } catch (NoSuchMethodException e) {
            throw new FakeJedisException("Unable to find FakeJedis method.", e);
        }
    }

    // //////////////////////
    // PUBLIC API
    // //////////////

    @Override public Response<Long> hincrBy(String key, String field, long value) {
        Action<Long> action = new Action<>(
                BuilderFactory.LONG,
                getMethod("hincrBy", String.class, String.class, long.class),
                key, field, value
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public Response<Long> del(String... keys) {
        Action<Long> action = new Action<>(
                BuilderFactory.LONG,
                getMethod("del", String[].class),
                keys
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public Response<Long> del(String key) {
        Action<Long> action = new Action<>(
                BuilderFactory.LONG,
                getMethod("del", String.class),
                key
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public Response<String> set(String key, String value) {
        Action<String> action = new Action<>(
                BuilderFactory.STRING,
                getMethod("set", String.class, String.class),
                key, value
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public List<Object> exec() {
        List<Object> results = new ArrayList<>(this.actions.size());
        synchronized (this.jedis.LOCK) {
            // We have the lock so we remove the multi to execute our commands
            this.jedis.setMulti(false);

            for (Action action : this.actions) {
                try {
                    Object result = action.method.invoke(this.jedis, action.args);
                    action.response.set(result);
                    results.add(result);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new FakeJedisException("An error occurred while executing a transaction", e);
                }
            }
        }
        return results;
    }

    // //////////////////////
    // MODEL
    // //////////////

    private static class Action<T> {
        public final Method method;
        public final Object[] args;
        public final Response<T> response;

        private Action(Builder<T> builder, Method method, Object... args) {
            this.method = method;
            this.args = args;
            this.response = new Response<>(builder);
        }
    }

    // //////////////////////
    // NOT IMPLEMENTED
    // //////////////

    @Override protected int getPipelinedResponseLength() {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected Client getClient(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected Client getClient(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public List<Response<?>> execGetResponse() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public String discard() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> brpop(String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> brpop(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> blpop(String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> blpop(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Map<String, String>> blpopMap(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> brpop(byte[]... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> brpop(int timeout, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Map<String, String>> brpopMap(int timeout, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> blpop(byte[]... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> blpop(int timeout, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> del(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> keys(String pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> keys(byte[] pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> mget(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> mget(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> mset(String... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> mset(byte[]... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> msetnx(String... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> msetnx(byte[]... keysvalues) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> rename(String oldkey, String newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> rename(byte[] oldkey, byte[] newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> renamenx(String oldkey, String newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> renamenx(byte[] oldkey, byte[] newkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> rpoplpush(String srckey, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> rpoplpush(byte[] srckey, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> sdiff(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> sdiff(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sdiffstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sdiffstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> sinter(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> sinter(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sinterstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sinterstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> smove(String srckey, String dstkey, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> smove(byte[] srckey, byte[] dstkey, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sort(String key, SortingParams sortingParameters, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sort(byte[] key, SortingParams sortingParameters, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sort(String key, String dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sort(byte[] key, byte[] dstkey) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> sunion(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> sunion(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sunionstore(String dstkey, String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sunionstore(byte[] dstkey, byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> watch(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> watch(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zinterstore(String dstkey, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zinterstore(byte[] dstkey, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zinterstore(String dstkey, ZParams params, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zunionstore(String dstkey, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zunionstore(byte[] dstkey, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zunionstore(String dstkey, ZParams params, String... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> bgrewriteaof() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> bgsave() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> configGet(String pattern) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> configSet(String parameter, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> brpoplpush(String source, String destination, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> brpoplpush(byte[] source, byte[] destination, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> configResetStat() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> save() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lastsave() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> publish(String channel, String message) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> publish(byte[] channel, byte[] message) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> randomKey() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> randomKeyBinary() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> flushDB() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> flushAll() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> info() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> time() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> dbSize() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> shutdown() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> ping() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> select(int index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitop(BitOP op, String destKey, String... srcKeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterNodes() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterMeet(String ip, int port) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterAddSlots(int... slots) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterDelSlots(int... slots) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterInfo() {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> clusterGetKeysInSlot(int slot, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterSetSlotNode(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterSetSlotMigrating(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> clusterSetSlotImporting(int slot, String nodeId) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> pfmerge(byte[] destkey, byte[]... sourcekeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> pfmerge(String destkey, String... sourcekeys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfcount(String... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfcount(byte[]... keys) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> append(String key, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> append(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> blpop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> brpop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> blpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> brpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> decr(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> decr(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> decrBy(String key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> decrBy(byte[] key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> del(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> echo(String string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> echo(byte[] string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> exists(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> exists(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> expire(String key, int seconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> expire(byte[] key, int seconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> expireAt(String key, long unixTime) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> expireAt(byte[] key, long unixTime) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> get(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> get(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> getbit(String key, long offset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> getbit(byte[] key, long offset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitpos(String key, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitpos(String key, boolean value, BitPosParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitpos(byte[] key, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitpos(byte[] key, boolean value, BitPosParams params) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> getrange(String key, long startOffset, long endOffset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> getSet(String key, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> getSet(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> getrange(byte[] key, long startOffset, long endOffset) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hdel(String key, String... field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hdel(byte[] key, byte[]... field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> hexists(String key, String field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> hexists(byte[] key, byte[] field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> hget(String key, String field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> hget(byte[] key, byte[] field) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Map<String, String>> hgetAll(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Map<byte[], byte[]>> hgetAll(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hincrBy(byte[] key, byte[] field, long value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> hkeys(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> hkeys(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hlen(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hlen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> hmget(String key, String... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> hmget(byte[] key, byte[]... fields) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> hmset(String key, Map<String, String> hash) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> hmset(byte[] key, Map<byte[], byte[]> hash) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hset(String key, String field, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hset(byte[] key, byte[] field, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hsetnx(String key, String field, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> hsetnx(byte[] key, byte[] field, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> hvals(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> hvals(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> incr(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> incr(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> incrBy(String key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> incrBy(byte[] key, long integer) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> lindex(String key, long index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> lindex(byte[] key, long index) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> linsert(String key, BinaryClient.LIST_POSITION where, String pivot, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> linsert(byte[] key, BinaryClient.LIST_POSITION where, byte[] pivot, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> llen(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> llen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> lpop(String key) {
        Action<String> action = new Action<>(
                BuilderFactory.STRING,
                getMethod("lpop", String.class),
                key
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public Response<byte[]> lpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lpush(String key, String... string) {
        Action<Long> action = new Action<>(
                BuilderFactory.LONG,
                getMethod("lpush", String.class, String[].class),
                key, string
        );
        this.actions.add(action);
        return action.response;
    }

    @Override public Response<Long> lpush(byte[] key, byte[]... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lpushx(String key, String... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lpushx(byte[] key, byte[]... bytes) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> lrange(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> lrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lrem(String key, long count, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> lrem(byte[] key, long count, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> lset(String key, long index, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> lset(byte[] key, long index, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> ltrim(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> ltrim(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> move(String key, int dbIndex) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> move(byte[] key, int dbIndex) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> persist(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> persist(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> rpop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> rpop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> rpush(String key, String... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> rpush(byte[] key, byte[]... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> rpushx(String key, String... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> rpushx(byte[] key, byte[]... string) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sadd(String key, String... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> sadd(byte[] key, byte[]... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> scard(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> scard(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> set(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> setbit(String key, long offset, boolean value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> setbit(byte[] key, long offset, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> setex(String key, int seconds, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> setex(byte[] key, int seconds, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> setnx(String key, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> setnx(byte[] key, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> setrange(String key, long offset, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> setrange(byte[] key, long offset, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> sismember(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Boolean> sismember(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> smembers(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> smembers(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> sort(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> sort(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> sort(String key, SortingParams sortingParameters) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> sort(byte[] key, SortingParams sortingParameters) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> spop(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> spop(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> srandmember(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<String>> srandmember(String key, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> srandmember(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<List<byte[]>> srandmember(byte[] key, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> srem(String key, String... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> srem(byte[] key, byte[]... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> strlen(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> strlen(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> substr(String key, int start, int end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> substr(byte[] key, int start, int end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> ttl(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> ttl(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> type(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> type(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zadd(String key, double score, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zadd(String key, Map<String, Double> scoreMembers) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zadd(byte[] key, double score, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zcard(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zcard(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zcount(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zcount(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zcount(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> zincrby(String key, double score, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> zincrby(byte[] key, double score, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrange(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByScore(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByScore(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByScore(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByScore(String key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByScore(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeByScoreWithScores(String key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeByScoreWithScores(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrevrangeByScore(String key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrevrangeByScore(byte[] key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrevrangeByScore(String key, String max, String min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrevrangeByScore(String key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrevrangeByScore(String key, String max, String min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override
    public Response<Set<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeWithScores(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrangeWithScores(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrank(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrank(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrem(String key, String... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrem(byte[] key, byte[]... member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByRank(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByRank(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByScore(String key, double start, double end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByScore(String key, String start, String end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByScore(byte[] key, double start, double end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByScore(byte[] key, byte[] start, byte[] end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrevrange(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrevrange(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeWithScores(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<Tuple>> zrevrangeWithScores(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrevrank(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zrevrank(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> zscore(String key, String member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> zscore(byte[] key, byte[] member) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zlexcount(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zlexcount(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByLex(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Set<String>> zrangeByLex(String key, String min, String max, int offset, int count) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByLex(byte[] key, byte[] min, byte[] max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> zremrangeByLex(String key, String min, String max) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitcount(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitcount(String key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> bitcount(byte[] key, long start, long end) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> dump(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> dump(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> migrate(String host, int port, String key, int destinationDb, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> migrate(byte[] host, int port, byte[] key, int destinationDb, int timeout) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> objectRefcount(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> objectRefcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> objectEncoding(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<byte[]> objectEncoding(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> objectIdletime(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> objectIdletime(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpire(String key, int milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpire(byte[] key, int milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpire(String key, long milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpire(byte[] key, long milliseconds) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpireAt(String key, long millisecondsTimestamp) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pexpireAt(byte[] key, long millisecondsTimestamp) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pttl(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pttl(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> restore(String key, int ttl, byte[] serializedValue) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> restore(byte[] key, int ttl, byte[] serializedValue) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> incrByFloat(String key, double increment) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> incrByFloat(byte[] key, double increment) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> psetex(String key, int milliseconds, String value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> psetex(byte[] key, int milliseconds, byte[] value) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> set(String key, String value, String nxxx) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> set(byte[] key, byte[] value, byte[] nxxx) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> set(String key, String value, String nxxx, String expx, int time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> set(byte[] key, byte[] value, byte[] nxxx, byte[] expx, int time) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> hincrByFloat(String key, String field, double increment) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Double> hincrByFloat(byte[] key, byte[] field, double increment) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> eval(String script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> eval(String script, List<String> keys, List<String> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> eval(String script, int numKeys, String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> evalsha(String script) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> evalsha(String sha1, List<String> keys, List<String> args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<String> evalsha(String sha1, int numKeys, String... args) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfadd(byte[] key, byte[]... elements) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfcount(byte[] key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfadd(String key, String... elements) {
        throw new FakeJedisNotImplementedException();
    }

    @Override public Response<Long> pfcount(String key) {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected void clean() {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected Response<?> generateResponse(Object data) {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected <T> Response<T> getResponse(Builder<T> builder) {
        throw new FakeJedisNotImplementedException();
    }

    @Override protected boolean hasPipelinedResponse() {
        throw new FakeJedisNotImplementedException();
    }
}
