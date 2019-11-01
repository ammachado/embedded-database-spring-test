package io.zonky.test.db.preparer;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Arrays;

import io.zonky.test.db.provider.DatabasePreparer;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class RecordingDataSourceTest {

    @Test
    public void testRecording() throws SQLException {
        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection = recordingDataSource.getConnection();
        connection.setAutoCommit(true);
        Statement statement = connection.createStatement();
        statement.executeUpdate("create table");
        statement.executeUpdate("insert data");
        statement.executeUpdate("select data");
        statement.close();
        connection.commit();
        connection.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).setAutoCommit(true);
        inOrder.verify(mockConnection).createStatement();
        inOrder.verify(mockStatement).executeUpdate("create table");
        inOrder.verify(mockStatement).executeUpdate("insert data");
        inOrder.verify(mockStatement).executeUpdate("select data");
        inOrder.verify(mockStatement).close();
        inOrder.verify(mockConnection).commit();
        inOrder.verify(mockConnection).close();
    }

    @Test
    public void testResultSet() throws SQLException {
        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection = recordingDataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select data");

        assertThat(resultSet).isNotNull();
        assertThat(resultSet.next()).isFalse();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(any())).thenReturn(mockResultSet);

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);

        verifyZeroInteractions(mockResultSet);
    }

    @Test
    public void testUnwrapping() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class);
        when(targetDataSource.unwrap(PGSimpleDataSource.class)).thenReturn(new PGSimpleDataSource());

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        PGSimpleDataSource unwrappedDataSource = recordingDataSource.unwrap(PGSimpleDataSource.class);
        unwrappedDataSource.getDescription();

        assertThat(unwrappedDataSource).isNotNull();

        DataSource mockDataSource = mock(DataSource.class);
        PGSimpleDataSource mockUnwrappedDataSource = mock(PGSimpleDataSource.class);
        when(mockDataSource.unwrap(PGSimpleDataSource.class)).thenReturn(mockUnwrappedDataSource);

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockUnwrappedDataSource);
        inOrder.verify(mockDataSource).unwrap(PGSimpleDataSource.class);
        inOrder.verify(mockUnwrappedDataSource).getDescription();
    }

    @Test
    public void testSnapshot() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        Savepoint savepoint = connection.setSavepoint();
        Statement statement = connection.createStatement();
        statement.executeUpdate("create table");
        connection.releaseSavepoint(savepoint);

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        Savepoint mockSavepoint = mock(Savepoint.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.setSavepoint()).thenReturn(mockSavepoint);
        when(mockConnection.createStatement()).thenReturn(mockStatement);

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);

        InOrder inOrder = inOrder(mockDataSource, mockConnection, mockStatement);
        inOrder.verify(mockDataSource).getConnection();
        inOrder.verify(mockConnection).setSavepoint();
        inOrder.verify(mockConnection).createStatement();
        inOrder.verify(mockStatement).executeUpdate("create table");
        inOrder.verify(mockConnection).releaseSavepoint(mockSavepoint);
    }

    @Test
    public void testBlobType() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
        when(targetDataSource.getConnection().createBlob()).thenReturn(new SerialBlob(new byte[4]));

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        Blob blob = connection.createBlob();
        blob.setBytes(1, new byte[] { 0, 1, 2, 3 });
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setBlob(1, blob);
        statement.executeUpdate();
        statement.close();
        blob.free();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);
        when(mockConnection.createBlob()).thenReturn(new SerialBlob(new byte[4]));

        // it is not possible to use Mockito#verify method
        // because the verification must take place immediately
        // when the PreparedStatement#setBlob method is called
        doNothing().when(mockStatement).setBlob(eq(1), argThat(new ArgumentMatcher<Blob>() {
            @Override
            public boolean matches(Object argument) {
                try {
                    Blob blob = (Blob) argument;
                    byte[] bytes = blob.getBytes(1, 4);
                    return Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 });
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);
    }

    @Test
    public void testInputStream() throws SQLException {
        DataSource targetDataSource = mock(DataSource.class, RETURNS_DEEP_STUBS);
        PreparedStatement preparedStatement = targetDataSource.getConnection().prepareStatement(any());
        doAnswer(invocation -> {
            InputStream stream = invocation.getArgumentAt(1, InputStream.class);
            byte[] bytes = IOUtils.readFully(stream, 4);
            checkState(Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 }));
            return null;
        }).when(preparedStatement).setBinaryStream(anyInt(), any());

        RecordingDataSource recordingDataSource = RecordingDataSource.wrap(targetDataSource);

        Connection connection = recordingDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("insert data");
        statement.setBinaryStream(1, new ByteArrayInputStream(new byte[] { 0, 1, 2, 3 }));
        statement.executeUpdate();
        statement.close();

        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(any())).thenReturn(mockStatement);

        doAnswer(invocation -> {
            InputStream stream = invocation.getArgumentAt(1, InputStream.class);
            byte[] bytes = IOUtils.readFully(stream, 4);
            checkState(Arrays.equals(bytes, new byte[] { 0, 1, 2, 3 }));
            return null;
        }).when(mockStatement).setBinaryStream(eq(1), any());

        DatabasePreparer preparer = recordingDataSource.buildPreparer();
        preparer.prepare(mockDataSource);

        verify(mockStatement).setBinaryStream(anyInt(), any());
    }

//    @Test
//    public void testExcludedMethods() throws SQLException {
//        ReplayableDatabasePreparer preparer = new ReplayableDatabasePreparer();
//        DataSource recordingDataSource = preparer.record(mock(DataSource.class, RETURNS_MOCKS));
//
//        recordingDataSource.
//
//        assertThat(unwrappedDataSource).isNotNull();
//
////        Connection connection = recordingDataSource.getConnection();
////        Statement statement = connection.createStatement();
////        statement.executeQuery("select data");
////
//////        assertThat(resultSet).isNotNull();
//////        assertThat(resultSet.next()).isFalse();
////
////        DataSource mockDataSource = mock(DataSource.class);
////        Connection mockConnection = mock(Connection.class);
////        Statement mockStatement = mock(Statement.class);
////
////        when(mockDataSource.getConnection()).thenReturn(mockConnection);
////        when(mockConnection.createStatement()).thenReturn(mockStatement);
////
////        preparer.prepare(mockDataSource);
////
////        verifyZeroInteractions(mockResultSet);
//    }

    @Test
    public void testEquals() throws SQLException {
        RecordingDataSource recordingDataSource1 = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));
        RecordingDataSource recordingDataSource2 = RecordingDataSource.wrap(mock(DataSource.class, RETURNS_MOCKS));

        Connection connection1 = recordingDataSource1.getConnection();
        connection1.setAutoCommit(true);
        Statement statement1 = connection1.createStatement();
        statement1.executeUpdate("create table");
        statement1.executeUpdate("insert data");
        statement1.executeUpdate("select data");
        statement1.close();
        connection1.commit();
        connection1.close();

        Connection connection2 = recordingDataSource2.getConnection();
        connection2.setAutoCommit(true);
        Statement statement2 = connection2.createStatement();
        statement2.executeUpdate("create table");
        statement2.executeUpdate("insert data");
        statement2.executeUpdate("select data");
        statement2.close();
        connection2.commit();
        connection2.close();

        DatabasePreparer databasePreparer1 = recordingDataSource1.buildPreparer();
        DatabasePreparer databasePreparer2 = recordingDataSource2.buildPreparer();

        assertThat(databasePreparer1).isEqualTo(databasePreparer2);
    }
}