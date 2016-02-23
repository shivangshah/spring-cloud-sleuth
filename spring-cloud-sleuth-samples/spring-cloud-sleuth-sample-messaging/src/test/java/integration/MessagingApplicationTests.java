/*
 * Copyright 2013-2015 the original author or authors.
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
package integration;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Random;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import integration.MessagingApplicationTests.IntegrationSpanCollectorConfig;
import sample.SampleMessagingApplication;
import tools.AbstractIntegrationTest;
import zipkin.Span;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { IntegrationSpanCollectorConfig.class, SampleMessagingApplication.class })
@WebIntegrationTest
@TestPropertySource(properties="sample.zipkin.enabled=true")
public class MessagingApplicationTests extends AbstractIntegrationTest {

	private static int port = 3381;
	private static String sampleAppUrl = "http://localhost:" + port;
	@Autowired IntegrationTestZipkinSpanReporter integrationTestSpanCollector;

	@After
	public void cleanup() {
		this.integrationTestSpanCollector.hashedSpans.clear();
	}

	@Test
	public void should_have_passed_trace_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
		});
	}

	@Test
	public void should_have_passed_trace_id_and_generate_new_span_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();
		long spanId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId, spanId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenTheSpansHaveProperParentStructure();
		});
	}

	@Test
	public void should_have_passed_trace_id_with_annotations_in_async_thread_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/xform", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenThereIsAtLeastOneBinaryAnnotationWithKey("background-sleep-millis");
		});
	}

	private void thenThereIsAtLeastOneBinaryAnnotationWithKey(String binaryAnnotationKey) {
		then(this.integrationTestSpanCollector.hashedSpans.stream()
				.map(s -> s.binaryAnnotations)
				.flatMap(Collection::stream)
				.anyMatch(b -> b.key.equals(binaryAnnotationKey))).isTrue();
	}

	private void thenAllSpansHaveTraceIdEqualTo(long traceId) {
		then(this.integrationTestSpanCollector.hashedSpans.stream().allMatch(span -> span.traceId == traceId)).isTrue();
	}

	private void thenTheSpansHaveProperParentStructure() {
		Optional<Span> firstHttpSpan = findFirstHttpRequestSpan();
		Optional<Span> firstHttpSpansParent = findFirstHttpRequestSpansParent();
		Optional<Span> lastHttpSpan = findLastHttpSpan();
		thenAllSpansArePresent(firstHttpSpan, firstHttpSpansParent, lastHttpSpan);
		then(lastHttpSpan.get().parentId).isEqualTo(firstHttpSpan.get().id);
		then(firstHttpSpan.get().parentId).isEqualTo(firstHttpSpansParent.get().id);
		then(firstHttpSpan.get().annotations).extracting("value").contains("message:messages");
	}

	private Optional<Span> findLastHttpSpan() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http:/foo".equals(span.name)).findFirst();
	}

	private Optional<Span> findFirstHttpRequestSpan() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http:/".equals(span.name) && span.parentId != null)
				.min((o1, o2) -> o1.timestamp.compareTo(o2.timestamp));
	}

	private Optional<Span> findFirstHttpRequestSpansParent() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http:/parent/".equals(span.name)).findFirst();
	}

	private void thenAllSpansArePresent(Optional<Span>... spans) {
		then(Arrays.asList(spans).stream().allMatch(Optional::isPresent)).isTrue();
	}

	@Configuration
	public static class IntegrationSpanCollectorConfig {
		@Bean
		ZipkinSpanReporter integrationTestZipkinSpanReporter() {
			return new IntegrationTestZipkinSpanReporter();
		}
	}

}
