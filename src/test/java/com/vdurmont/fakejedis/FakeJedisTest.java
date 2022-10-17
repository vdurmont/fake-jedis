package com.vdurmont.fakejedis;


import org.hamcrest.core.StringContains;
import org.hamcrest.core.StringEndsWith;
import org.hamcrest.core.StringStartsWith;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class FakeJedisTest {
    private static final String KEY = "my_key";
    private static final String FIELD = "my_field";
    private static final String VALUE = "my_value";

    private Jedis jedis;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before public void setUp() {
        this.jedis = new FakeJedis();
    }

    @Test public void lpush_returns_the_length_of_the_list() {
        // GIVEN

        // WHEN
        for (int i = 1; i <= 10; i++) {
            long len = this.jedis.lpush(KEY, VALUE + "_" + i);

            // THEN
            assertEquals(i, len);
        }
    }

    @Test public void lpush_and_lpop() {
        // GIVEN

        // WHEN
        long len = this.jedis.lpush(KEY, VALUE);
        String result = this.jedis.lpop(KEY);
        long len2 = this.jedis.llen(KEY);

        // THEN
        assertEquals(1, len);
        assertEquals(VALUE, result);
        assertEquals(0, len2);
    }

    @Test public void llen_on_a_null_list_returns_0() {
        // GIVEN

        // WHEN
        long len = this.jedis.llen(KEY);

        // THEN
        assertEquals(0, len);
    }

    @Test public void hset_if_new_field_returns_1() {
        // GIVEN

        // WHEN
        long result = this.jedis.hset(KEY, FIELD, VALUE);

        // THEN
        assertEquals(1, result);
    }

    @Test public void hset_if_updated_field_returns_0() {
        // GIVEN

        // WHEN
        this.jedis.hset(KEY, FIELD, VALUE);
        long result = this.jedis.hset(KEY, FIELD, VALUE);

        // THEN
        assertEquals(0, result);
    }

    @Test public void hdel_if_removed_returns_1() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, VALUE);

        // WHEN
        final long result = this.jedis.hdel(KEY, FIELD, VALUE);

        // THEN
        assertEquals(1, result);
    }

    @Test public void hdel_if_not_found_returns_0() {
        // GIVEN

        // WHEN
        final long result = this.jedis.hdel(KEY, FIELD, VALUE);

        // THEN
        assertEquals(0, result);
    }

    @Test public void hset_and_hget() {
        // GIVEN

        // WHEN
        this.jedis.hset(KEY, FIELD, VALUE);
        String result = this.jedis.hget(KEY, FIELD);

        // THEN
        assertEquals(VALUE, result);
    }

    @Test public void hdel_removes_value() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, VALUE);

        // WHEN
        this.jedis.hdel(KEY, FIELD);
        String result = this.jedis.hget(KEY, FIELD);

        // THEN
        assertNull(result);
    }

    @Test public void hset_replaces_the_old_value() {
        // GIVEN
        String newValue = "my_new_value";
        this.jedis.hset(KEY, FIELD, VALUE);

        // WHEN
        this.jedis.hset(KEY, FIELD, newValue);

        // THEN
        String result = this.jedis.hget(KEY, FIELD);
        assertEquals(newValue, result);
    }

    @Test public void llen_on_a_hash() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, VALUE);

        // THEN
        this.expectedException.expect(JedisDataException.class);
        this.expectedException.expectMessage("WRONGTYPE Operation against a key holding the wrong kind of value");

        // WHEN
        this.jedis.llen(KEY);
    }

    @Test public void hincrby_on_null_sets_the_increment_value() {
        // GIVEN
        long incr = 3;

        // WHEN
        long result = this.jedis.hincrBy(KEY, FIELD, incr);

        // THEN
        assertEquals(incr, result);
        assertEquals(incr, (long) Long.valueOf(this.jedis.hget(KEY, FIELD)));
    }

    @Test public void hincrby_increments_the_current_value() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, "4");

        // WHEN
        long result = this.jedis.hincrBy(KEY, FIELD, 3);

        // THEN
        assertEquals(7, result);
        assertEquals(7, (long) Long.valueOf(this.jedis.hget(KEY, FIELD)));
    }

    @Test public void hincrby_on_a_non_integer_field() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, VALUE);

        // THEN
        this.expectedException.expect(JedisDataException.class);
        this.expectedException.expectMessage("ERR hash value is not an integer");

        // WHEN
        this.jedis.hincrBy(KEY, FIELD, 3);
    }

    @Test public void lrange_on_empty_key() {
        // GIVEN

        // WHEN
        List<String> list = this.jedis.lrange(KEY, 0, 1000);

        // THEN
        assertEquals(0, list.size());
    }

    @Test public void lrange_on_the_whole_list() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, 0, -1);

        // THEN
        assertEquals(10, list.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("value_" + (10 - i), list.get(i));
        }
    }

    private static void initList(Jedis jedis) {
        for (int i = 1; i <= 10; i++) {
            jedis.lpush(KEY, "value_" + i);
        }
    }

    @Test public void lrange_indexes() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, 1, 4);

        // THEN
        assertEquals(4, list.size());
        assertEquals("value_9", list.get(0));
        assertEquals("value_8", list.get(1));
        assertEquals("value_7", list.get(2));
        assertEquals("value_6", list.get(3));
    }

    @Test public void lrange_negative_indexes() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, -3, -2);

        // THEN
        assertEquals(2, list.size());
        assertEquals("value_3", list.get(0));
        assertEquals("value_2", list.get(1));
    }

    @Test public void lrange_negative_indexes_reversed() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, -2, -3);

        // THEN
        assertEquals(0, list.size());
    }

    @Test public void lrange_indexes_reversed() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, 5, 3);

        // THEN
        assertEquals(0, list.size());
    }

    @Test public void lrange_out_of_bound_indexes() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, 0, 20);

        // THEN
        assertEquals(10, list.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("value_" + (10 - i), list.get(i));
        }
    }

    @Test public void lrange_out_of_bound_negative_indexes() {
        // GIVEN
        initList(this.jedis);

        // WHEN
        List<String> list = this.jedis.lrange(KEY, -20, -1);

        // THEN
        assertEquals(10, list.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("value_" + (10 - i), list.get(i));
        }
    }

    @Test public void exists_if_doesnt_exist_returns_false() {
        // GIVEN

        // WHEN
        boolean result = this.jedis.exists(KEY);

        // THEN
        assertFalse(result);
    }

    @Test public void exists_if_exists_returns_true() {
        // GIVEN
        String key2 = KEY + "2";

        this.jedis.lpush(KEY, VALUE);
        this.jedis.hset(key2, FIELD, VALUE);

        // WHEN
        boolean resultList = this.jedis.exists(KEY);
        boolean resultHash = this.jedis.exists(key2);

        // THEN
        assertTrue(resultList);
        assertTrue(resultHash);
    }

    @Test public void del_multi_removes_the_keys() {
        // GIVEN
        String key2 = KEY + "2";

        this.jedis.lpush(KEY, VALUE);
        this.jedis.lpush(key2, VALUE);

        // WHEN
        long result = this.jedis.del(KEY, key2);

        // THEN
        assertEquals(2, result);
        assertFalse(this.jedis.exists(KEY));
        assertFalse(this.jedis.exists(key2));
    }

    @Test public void del_multi_with_unknown_keys() {
        // GIVEN
        String key2 = KEY + "2";
        String key3 = KEY + "3";
        String key4 = KEY + "4";
        String key5 = KEY + "5";

        this.jedis.lpush(KEY, VALUE);
        this.jedis.lpush(key2, VALUE);

        // WHEN
        long result = this.jedis.del(KEY, key2, key3, key4, key5);

        // THEN
        assertEquals(2, result);
        assertFalse(this.jedis.exists(KEY));
        assertFalse(this.jedis.exists(key2));
        assertFalse(this.jedis.exists(key3));
        assertFalse(this.jedis.exists(key4));
        assertFalse(this.jedis.exists(key5));

    }

    @Test public void del_unknown_key_returns_0() {
        // GIVEN

        // WHEN
        long result = this.jedis.del(KEY);

        // THEN
        assertFalse(this.jedis.exists(KEY));
        assertEquals(0, result);
    }

    @Test public void del_removes_the_key() {
        // GIVEN
        this.jedis.lpush(KEY, VALUE);

        // WHEN
        long result = this.jedis.del(KEY);

        // THEN
        assertFalse(this.jedis.exists(KEY));
        assertEquals(1, result);
    }

    @Test public void unlink_unknown_key_returns_0() {
        // GIVEN

        // WHEN
        long result = this.jedis.unlink(KEY);

        // THEN
        assertFalse(this.jedis.exists(KEY));
        assertEquals(0, result);
    }

    @Test public void unlink_removes_the_key() {
        // GIVEN
        this.jedis.lpush(KEY, VALUE);

        // WHEN
        long result = this.jedis.unlink(KEY);

        // THEN
        assertFalse(this.jedis.exists(KEY));
        assertEquals(1, result);
    }

    @Test public void set_defines_the_value() {
        // GIVEN

        // WHEN
        String result = this.jedis.set(KEY, VALUE);

        // THEN
        assertEquals(VALUE, this.jedis.get(KEY));
        assertEquals("OK", result);
    }

    @Test public void keys_with_a_star_returns_all_the_keys() {
        // GIVEN
        this.jedis.set("test", VALUE);
        this.jedis.set("testing", VALUE);

        // WHEN
        Set<String> keys = this.jedis.keys("*");

        // THEN
        assertEquals(2, keys.size());
        assertTrue(keys.contains("test"));
        assertTrue(keys.contains("testing"));
    }

    @Test public void keys_matches_the_pattern() {
        // GIVEN
        this.jedis.set("test", VALUE);
        this.jedis.set("testing", VALUE);

        // WHEN
        Set<String> keys = this.jedis.keys("testi*");

        // THEN
        assertEquals(1, keys.size());
        assertTrue(keys.contains("testing"));
    }

    @Test public void keys_with_dashes() {
        // GIVEN
        this.jedis.set("test-with-dashes", VALUE);
        this.jedis.set("testing", VALUE);

        // WHEN
        Set<String> keys = this.jedis.keys("*with-dashes*");

        // THEN
        assertEquals(1, keys.size());
        assertTrue(keys.contains("test-with-dashes"));
    }

    @Test public void hgetall_with_unknown_key() {
        // GIVEN

        // WHEN
        Map<String, String> result = this.jedis.hgetAll(KEY);

        // THEN
        assertEquals(0, result.size());
    }

    @Test public void hgetall_with_hash() {
        // GIVEN
        this.jedis.hset(KEY, FIELD, VALUE);
        String field2 = FIELD + "2";
        String value2 = VALUE + "2";
        this.jedis.hset(KEY, field2, value2);

        // WHEN
        Map<String, String> result = this.jedis.hgetAll(KEY);

        // THEN
        assertEquals(2, result.size());
        assertEquals(VALUE, result.get(FIELD));
        assertEquals(value2, result.get(field2));
    }

    @Test public void hgetall_on_a_list() {
        // GIVEN
        this.jedis.lpush(KEY, VALUE);

        // THEN
        this.expectedException.expect(JedisDataException.class);
        this.expectedException.expectMessage("WRONGTYPE Operation against a key holding the wrong kind of value");

        // WHEN
        this.jedis.hgetAll(KEY);
    }

    @Test public void setnx_if_already_exists() {
        // GIVEN
        String value2 = VALUE + "2";
        this.jedis.set(KEY, VALUE);

        // WHEN
        long result = this.jedis.setnx(KEY, value2);

        // THEN
        assertEquals(0, result);
        assertEquals(VALUE, this.jedis.get(KEY));
    }

    @Test public void setnx_if_doesnt_exist() {
        // GIVEN

        // WHEN
        long result = this.jedis.setnx(KEY, VALUE);

        // THEN
        assertEquals(1, result);
        assertEquals(VALUE, this.jedis.get(KEY));
    }

    // ---------

    @Test public void sadd_if_doesnt_exist() {
        // GIVEN

        // WHEN
        long result = this.jedis.sadd(KEY, VALUE);

        // THEN
        assertEquals(1, result);
        final Set<String> members = this.jedis.smembers(KEY);
        assertEquals(1, members.size());
        assertTrue(members.contains(VALUE));
    }

    @Test public void sadd_if_it_exists() {
        // GIVEN

        // WHEN
        this.jedis.sadd(KEY, VALUE);
        long result = this.jedis.sadd(KEY, VALUE);

        // THEN
        assertEquals(0, result);
        final Set<String> members = this.jedis.smembers(KEY);
        assertEquals(1, members.size());
        assertTrue(members.contains(VALUE));
    }

    @Test public void srem_if_it_exists() {
        // GIVEN

        // WHEN
        this.jedis.sadd(KEY, VALUE);
        long result = this.jedis.srem(KEY, VALUE);

        // THEN
        assertEquals(1, result);

        final Set<String> members = this.jedis.smembers(KEY);
        assertTrue(members.isEmpty());
    }

    @Test public void srem_if_it_doesnt_exist() {
        // GIVEN

        // WHEN
        long result = this.jedis.srem(KEY, VALUE);

        // THEN
        assertEquals(0, result);

        final Set<String> members = this.jedis.smembers(KEY);
        assertTrue(members.isEmpty());
    }

    @Test public void sismember_when_not_in_set() {
        // GIVEN

        // WHEN
        boolean result = this.jedis.sismember(KEY, VALUE);

        // THEN
        assertFalse(result);
    }

    @Test public void sismember_when_in_set() {
        // GIVEN
        this.jedis.sadd(KEY, VALUE);

        // WHEN
        boolean result = this.jedis.sismember(KEY, VALUE);

        // THEN
        assertTrue(result);
    }

    @Test public void call_a_method_that_is_not_implemented() {
        // GIVEN

        // THEN
        this.expectedException.expect(FakeJedisNotImplementedException.class);
        this.expectedException.expectMessage(StringStartsWith.startsWith("The method "));
        this.expectedException.expectMessage(StringContains.containsString("FakeJedis.mget"));
        this.expectedException.expectMessage(StringEndsWith.endsWith(" is not implemented in your version of FakeJedis. Contribute on github! https://github.com/vdurmont/fake-jedis"));

        // WHEN
        this.jedis.mget(KEY);
    }

    @Test public void if_isInMulti_we_cant_use_the_instance_anymore() {
        // GIVEN
        this.jedis.multi();

        // THEN
        this.expectedException.expect(JedisDataException.class);
        this.expectedException.expectMessage("Cannot use Jedis when in Multi. Please use JedisTransaction instead.");

        // WHEN
        this.jedis.exists(KEY);
    }

    @Test public void if_was_in_multi_but_exec_was_called_the_instance_can_be_used() {
        // GIVEN
        Transaction tr = this.jedis.multi();
        tr.lpush(KEY, VALUE);
        tr.exec();

        // WHEN
        boolean result = this.jedis.exists(KEY);

        // THEN
        assertTrue(result);
    }

    @Test public void thread_safety() throws InterruptedException {
        // GIVEN
        Thread t1 = new Thread(() -> IntStream.range(0, 1000).forEach(i -> this.jedis.hincrBy(KEY, FIELD, i)));
        Thread t2 = new Thread(() -> IntStream.range(0, 1000).forEach(i -> this.jedis.hincrBy(KEY, FIELD, -i)));

        // WHEN
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // THEN
        String result = this.jedis.hget(KEY, FIELD);
        assertEquals("0", result);
    }
}
