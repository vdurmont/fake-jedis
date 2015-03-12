package com.vdurmont.fakejedis;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(JUnit4.class)
public class FakeTransactionTest {
    private static final String KEY = "my_key";
    private static final String FIELD = "my_field";
    private static final String VALUE = "my_value";

    private Jedis jedis;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before public void setUp() {
        this.jedis = new FakeJedis();
    }

    @Test public void hincrby() {
        // GIVEN
        Transaction tr = this.jedis.multi();

        // WHEN
        Response<Long> result1 = tr.hincrBy(KEY, FIELD, 3);
        Response<Long> result2 = tr.hincrBy(KEY, FIELD, 4);
        List<Object> results = tr.exec();

        // THEN
        String value = this.jedis.hget(KEY, FIELD);
        assertEquals("7", value);

        assertEquals(2, results.size());
        assertEquals(3, (long) results.get(0));
        assertEquals(7, (long) results.get(1));

        assertEquals(3, (long) result1.get());
        assertEquals(7, (long) result2.get());
    }

    @Test public void responseget_before_exec() {
        // GIVEN
        Transaction tr = this.jedis.multi();

        // THEN
        this.expectedException.expect(JedisDataException.class);
        this.expectedException.expectMessage("Please close pipeline or multi block before calling this method.");

        // WHEN
        Response<Long> response = tr.hincrBy(KEY, FIELD, 3);
        response.get();
    }

    @Test public void del() {
        // GIVEN
        this.jedis.lpush(KEY, VALUE);
        Transaction tr = this.jedis.multi();

        // WHEN
        tr.del(KEY);
        tr.exec();

        // THEN
        assertFalse(this.jedis.exists(KEY));
    }
}
