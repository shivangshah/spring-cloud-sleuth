/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import java.util.Random;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.sampler.NeverSampler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * @author Dave Syer
 *
 */
public class TraceChannelInterceptor extends AbstractTraceChannelInterceptor {

	public TraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys, Random random) {
		super(tracer, traceKeys, random);
	}

	@Override
	public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
		Span span = SpanMessageHeaders.getSpanFromHeader(message);
		Boolean created = SpanMessageHeaders.getSpanCreatedFromHeader(message);
		if (Boolean.TRUE.equals(created)) {
			getTracer().close(span);
		}
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		Span parentSpan = getTracer().isTracing() ? getTracer().getCurrentSpan()
				: buildSpan(message);
		boolean created = !getTracer().isTracing();
		String name = getMessageChannelName(channel);
		Span span = startSpan(parentSpan, created, name, message);
		return SpanMessageHeaders.addSpanHeaders(getTraceKeys(), message, span, created);
	}

	private Span startSpan(Span parentSpan, boolean created, String name, Message<?> message) {
		if (message.getHeaders().containsKey(Span.NOT_SAMPLED_NAME)) {
			return getTracer().startTrace(name, NeverSampler.INSTANCE);
		}
		if (parentSpan == null) {
			return getTracer().startTrace(name);
		}
		Span span = span(parentSpan, created, name);
		span.logEvent(name);
		return span;
	}

	private Span span(Span parentSpan, boolean created, String name) {
		if (!created) {
			return getTracer().continueSpan(parentSpan);
		}
		return getTracer().joinTrace(name, parentSpan);
	}

	@Override
	public Message<?> beforeHandle(Message<?> message, MessageChannel channel,
			MessageHandler handler) {
		getTracer().continueSpan(SpanMessageHeaders.getSpanFromHeader(message));
		return message;
	}

	@Override
	public void afterMessageHandled(Message<?> message, MessageChannel channel,
			MessageHandler handler, Exception ex) {
		getTracer().detach(SpanMessageHeaders.getSpanFromHeader(message));
	}

}
