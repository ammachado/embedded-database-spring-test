/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zonky.test.db.provider.impl;

import io.zonky.test.db.provider.DatabasePreparer;
import io.zonky.test.db.provider.DatabaseResult;
import io.zonky.test.db.provider.TemplatableDatabaseProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.zonky.test.assertj.MockitoAssertions.mockWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class PrefetchingDatabaseProviderTest {

    @Mock
    private TemplatableDatabaseProvider databaseProvider;

    private PrefetchingDatabaseProvider prefetchingProvider;

    @Before
    public void setUp() {
        prefetchingProvider = new PrefetchingDatabaseProvider(databaseProvider, new MockEnvironment());
    }

    @Test
    public void testPrefetching() throws Exception {
        DatabasePreparer preparer = mock(DatabasePreparer.class);
        List<DataSource> dataSources = Stream.generate(() -> mock(DataSource.class))
                .limit(6).collect(Collectors.toList());

        BlockingQueue<DataSource> providerReturns = new LinkedBlockingQueue<>(dataSources);
        doAnswer(i -> new DatabaseResult(providerReturns.poll(), null)).when(databaseProvider).createDatabase(same(preparer));

        Set<DataSource> results = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            results.add(prefetchingProvider.createDatabase(preparer).getDataSource());
        }
        assertThat(results).hasSize(3).isSubsetOf(dataSources);

        verify(databaseProvider, timeout(100).times(6)).createDatabase(same(preparer));
    }

    // TODO: move into DatabaseProvidersTest
//    @Test
//    public void testMultipleProviders() throws Exception {
//        DatabasePreparer preparer = mock(DatabasePreparer.class);
//
//        doAnswer(i -> mock(DataSource.class, "mockDataSource1")).when(databaseProvider1).createDatabase(any());
//        doAnswer(i -> mock(DataSource.class, "mockDataSource2")).when(databaseProvider2).createDatabase(any());
//        doAnswer(i -> mock(DataSource.class, "mockDataSource3")).when(databaseProvider3).createDatabase(any());
//
//        for (int i = 0; i < 3; i++) {
//            assertThat(prefetchingProvider.createDatabase(preparer, newDescriptor("database1", "provider1"))).is(mockWithName("mockDataSource1"));
//            assertThat(prefetchingProvider.createDatabase(preparer, newDescriptor("database2", "provider1"))).is(mockWithName("mockDataSource2"));
//            assertThat(prefetchingProvider.createDatabase(preparer, newDescriptor("database2", "provider2"))).is(mockWithName("mockDataSource3"));
//        }
//
//        verify(databaseProvider1, timeout(100).times(6)).createDatabase(same(preparer));
//        verify(databaseProvider2, timeout(100).times(6)).createDatabase(same(preparer));
//        verify(databaseProvider3, timeout(100).times(6)).createDatabase(same(preparer));
//    }

    @Test
    public void testDifferentPreparers() throws Exception {
        DatabasePreparer preparer1 = mock(DatabasePreparer.class);
        DatabasePreparer preparer2 = mock(DatabasePreparer.class);
        DatabasePreparer preparer3 = mock(DatabasePreparer.class);

        doAnswer(i -> new DatabaseResult(mock(DataSource.class, "mockDataSource1"), null)).when(databaseProvider).createDatabase(same(preparer1));
        doAnswer(i -> new DatabaseResult(mock(DataSource.class, "mockDataSource2"), null)).when(databaseProvider).createDatabase(same(preparer2));
        doAnswer(i -> new DatabaseResult(mock(DataSource.class, "mockDataSource3"), null)).when(databaseProvider).createDatabase(same(preparer3));

        assertThat(prefetchingProvider.createDatabase(preparer1).getDataSource()).is(mockWithName("mockDataSource1"));
        assertThat(prefetchingProvider.createDatabase(preparer2).getDataSource()).is(mockWithName("mockDataSource2"));
        assertThat(prefetchingProvider.createDatabase(preparer3).getDataSource()).is(mockWithName("mockDataSource3"));

        verify(databaseProvider, timeout(100).times(12)).createDatabase(any(DatabasePreparer.class));
    }
}