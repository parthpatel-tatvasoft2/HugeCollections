/*
 * Copyright 2014 Higher Frequency Trading
 * <p/>
 * http://www.higherfrequencytrading.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Rob Austin.
 */
public class JDBCReplicatorTest {

    private Connection connection;
    private Statement stmt;

    @Before
    public void setup() throws SQLException {
        final String dbURL = "jdbc:derby:memory:openhft;create=true";
        connection = DriverManager.getConnection(dbURL);
        connection.setAutoCommit(true);
        stmt = connection.createStatement();
    }

    @After
    public void after() throws SQLException {

        if (stmt != null)
            stmt.close();

        if (connection != null)
            connection.close();
    }

    /**
     * an example of the embedded in memory JDBC database connectivity
     *
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    @Test
    public void testSimpleJDBCConnectivity() throws ClassNotFoundException, SQLException {

        String tableName = createUniqueTableName();

        stmt.executeUpdate("create table " + tableName +
                " (ID integer NOT NULL, " +
                "NAME varchar(40) NOT NULL, " +
                "PRIMARY KEY (ID))");

        stmt.execute("insert into " + tableName + " (ID,NAME) values (1,'rob')");

        final ResultSet resultSets = stmt.executeQuery("select * from " + tableName);
        resultSets.next();

        Assert.assertEquals(1, resultSets.getInt("ID"));
        Assert.assertEquals("rob", resultSets.getString("NAME"));

    }


    @Test
    public void testJDBCWithCustomFieldMapper() throws ClassNotFoundException, SQLException {

        String tableName = createUniqueTableName();
        String createString =
                "create table " + tableName + " " +
                        "(ID integer NOT NULL, " +
                        "F1 varchar(40) NOT NULL, " +
                        "PRIMARY KEY (ID))";

        stmt.executeUpdate(createString);

        final JDBCReplicator jdbcCReplicator = new JDBCReplicator(Object.class, new JDBCReplicator.Fields() {

            @Override
            public CharSequence keyName() {
                return "ID";
            }

            @Override
            public Map<java.lang.reflect.Field, String> columnsNamesByField() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<Field> getFields(Object value, boolean skipKey) {
                return Collections.singleton(new Field("F1", "'Rob'"));
            }
        }, stmt, tableName);


        jdbcCReplicator.onUpdate("1", "F1");
        ResultSet resultSets = stmt.executeQuery("select * from " + tableName);

        resultSets.next();

        Assert.assertEquals("Rob", resultSets.getString("F1"));


    }


    @Test
    public void testJDBCWithAnnotationBasedFieldMapper() throws ClassNotFoundException, SQLException {

        class BeanClass {

            @Key(name = "ID")
            int id;

            @Column(name = "NAME")
            String name;

            @Column(name = "DOUBLE_VAL")
            double doubleValue;

            @Column(name = "DATE_VAL")
            Date dateValue;

            @Column(name = "CHAR_VAL")
            char c;

            @Column(name = "BOOL_VAL")
            boolean bool;

            @Column(name = "SHORT_VAL")
            short shortVal;


            BeanClass(int id, String name, double doubleValue, Date dateValue, char c, boolean bool, short shortVal) {
                this.id = id;
                this.name = name;
                this.doubleValue = doubleValue;
                this.dateValue = dateValue;
                this.c = c;
                this.bool = bool;
                this.shortVal = shortVal;
            }
        }

        final String tableName = createUniqueTableName();

        stmt.executeUpdate("create table " + tableName + " (" +
                "ID integer NOT NULL, " +
                "NAME varchar(40) NOT NULL, " +
                "CHAR_VAL char(1) NOT NULL, " +
                "DOUBLE_VAL REAL," +
                "SHORT_VAL SMALLINT," +
                "DATE_VAL DATE," +
                "BOOL_VAL BOOLEAN," +
                "PRIMARY KEY (ID))");

        final JDBCReplicator<Object, BeanClass, SharedHashMap<Object, BeanClass>> jdbcCReplicator = new JDBCReplicator<Object, BeanClass, SharedHashMap<Object, BeanClass>>(BeanClass.class, stmt, tableName);
        final Date expectedDate = new Date(0);
        final BeanClass bean = new BeanClass(1, "Rob", 1.234, expectedDate, 'c', false, (short) 1);

        jdbcCReplicator.onUpdate(bean.id, bean);

        final ResultSet resultSets = stmt.executeQuery("select * from " + tableName);

        resultSets.next();

        Assert.assertEquals("Rob", resultSets.getString("NAME"));
        Assert.assertEquals(1.234, resultSets.getDouble("DOUBLE_VAL"), 0.001);
        Assert.assertEquals("c", resultSets.getString("CHAR_VAL"));
        Assert.assertEquals(false, resultSets.getBoolean("BOOL_VAL"));
        Assert.assertEquals(1, resultSets.getShort("SHORT_VAL"));
        final java.sql.Date expected = new java.sql.Date(expectedDate.getTime());

        Assert.assertEquals(expected.toLocalDate(), resultSets.getDate("DATE_VAL").toLocalDate());

    }

    private static int sequenceNumber;

    private static String createUniqueTableName() {
        return "dbo.Test" + (sequenceNumber++);
    }


    /**
     * get back a Map of all the rows in the table, the map is keyed on the tables key
     *
     * @throws ClassNotFoundException
     * @throws SQLException
     * @throws InstantiationException
     */
    @Test
    public void testJDBCBulkLoading() throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException {

        class BeanClass {

            @Key(name = "ID")
            int id;

            @Column(name = "NAME")
            String name;

            BeanClass(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String toString() {
                return "BeanClass{" +
                        "id=" + id +
                        ", name='" + name + '\'' +
                        '}';
            }
        }

        final String tableName = createUniqueTableName();

        stmt.executeUpdate("create table " + tableName + " (" +
                "ID integer NOT NULL, " +
                "NAME varchar(40) NOT NULL, " +
                "PRIMARY KEY (ID))");

        final JDBCReplicator<Object, BeanClass, SharedHashMap<Object, BeanClass>> jdbcCReplicator =
                new JDBCReplicator<Object, BeanClass,
                        SharedHashMap<Object, BeanClass>>(BeanClass.class, stmt, tableName);

        for (BeanClass bean : new BeanClass[]{
                new BeanClass(1, "Rob"),
                new BeanClass(2, "Peter"),
                new BeanClass(3, "Daniel"),
                new BeanClass(4, "Vicky")}) {

            jdbcCReplicator.onInsert(bean.id, bean);
        }

        final Map<Object, BeanClass> result = jdbcCReplicator.getAll();
        Assert.assertEquals(4, result.size());

    }


    @Test
    public void testJDBCLoadingASingleField() throws ClassNotFoundException, SQLException,
            InstantiationException, IllegalAccessException {

        class BeanClass {

            @Key(name = "ID")
            int id;

            @Column(name = "NAME")
            String name;

            BeanClass(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String toString() {
                return "BeanClass{" +
                        "id=" + id +
                        ", name='" + name + '\'' +
                        '}';
            }
        }

        final String tableName = createUniqueTableName();

        stmt.executeUpdate("create table " + tableName + " (" +
                "ID integer NOT NULL, " +
                "NAME varchar(40) NOT NULL, " +
                "PRIMARY KEY (ID))");

        final JDBCReplicator<Object, BeanClass, SharedHashMap<Object, BeanClass>> jdbcCReplicator =
                new JDBCReplicator<Object, BeanClass,
                        SharedHashMap<Object, BeanClass>>(BeanClass.class, stmt, tableName);

        for (BeanClass bean : new BeanClass[]{
                new BeanClass(1, "Rob"),
                new BeanClass(2, "Peter"),
                new BeanClass(3, "Daniel"),
                new BeanClass(4, "Vicky")}) {

            jdbcCReplicator.onInsert(bean.id, bean);
        }

        final Map<Object, BeanClass> result = jdbcCReplicator.getAll();
        Assert.assertEquals(4, result.size());

        final BeanClass beanClass = jdbcCReplicator.get(1);
        Assert.assertEquals("Rob", beanClass.name);

    }


    @Test
    public void testJDBCLoadingAListOfFields() throws ClassNotFoundException, SQLException,
            InstantiationException, IllegalAccessException {

        class BeanClass {

            @Key(name = "ID")
            int id;

            @Column(name = "NAME")
            String name;

            BeanClass(int id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public String toString() {
                return "BeanClass{" +
                        "id=" + id +
                        ", name='" + name + '\'' +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                BeanClass beanClass = (BeanClass) o;

                if (id != beanClass.id) return false;
                if (name != null ? !name.equals(beanClass.name) : beanClass.name != null) return false;

                return true;
            }

            @Override
            public int hashCode() {
                int result = id;
                result = 31 * result + (name != null ? name.hashCode() : 0);
                return result;
            }
        }

        final String tableName = createUniqueTableName();

        stmt.executeUpdate("create table " + tableName + " (" +
                "ID integer NOT NULL, " +
                "NAME varchar(40) NOT NULL, " +
                "PRIMARY KEY (ID))");

        final JDBCReplicator<Object, BeanClass, SharedHashMap<Object, BeanClass>> jdbcCReplicator =
                new JDBCReplicator<Object, BeanClass,
                        SharedHashMap<Object, BeanClass>>(BeanClass.class, stmt, tableName);

        final BeanClass rob = new BeanClass(1, "Rob");
        final BeanClass peter = new BeanClass(2, "Peter");
        for (BeanClass bean : new BeanClass[]{
                rob,
                peter,
                new BeanClass(3, "Daniel"),
                new BeanClass(4, "Vicky")}) {

            jdbcCReplicator.onInsert(bean.id, bean);
        }

        final Map<Object, BeanClass> result = jdbcCReplicator.getAll();
        Assert.assertEquals(4, result.size());

        final Set<BeanClass> beanClass = jdbcCReplicator.get(1, 2, 3);
        Assert.assertTrue(beanClass.contains(rob));

    }

}


