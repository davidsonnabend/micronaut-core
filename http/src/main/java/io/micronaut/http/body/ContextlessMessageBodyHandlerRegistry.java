/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MessageBodyHandlerRegistry} implementation that does not need an application context.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
@Experimental
public final class ContextlessMessageBodyHandlerRegistry extends AbstractMessageBodyHandlerRegistry {
    private final List<ReaderEntry> readerEntries = new ArrayList<>();
    private final List<WriterEntry> writerEntries = new ArrayList<>();
    private final List<TypedMessageBodyReader<?>> typedMessageBodyReaders;
    private final List<TypedMessageBodyWriter<?>> typedMessageBodyWriters;

    /**
     * @param applicationConfiguration The configuration
     * @param byteBufferFactory        The buffer factory
     * @param otherRawHandlers         Raw handlers to add on top of the default ones
     */
    public ContextlessMessageBodyHandlerRegistry(ApplicationConfiguration applicationConfiguration,
                                                 ByteBufferFactory<?, ?> byteBufferFactory,
                                                 TypedMessageBodyHandler<?>... otherRawHandlers) {
        this.typedMessageBodyReaders = new ArrayList<>(3 + otherRawHandlers.length);
        this.typedMessageBodyReaders.add(new StringBodyReader(applicationConfiguration));
        this.typedMessageBodyReaders.add(new ByteArrayBodyHandler());
        this.typedMessageBodyReaders.add(new ByteBufferBodyHandler(byteBufferFactory));
        this.typedMessageBodyWriters = new ArrayList<>(3 + otherRawHandlers.length);
        this.typedMessageBodyWriters.add(new CharSequenceBodyWriter(applicationConfiguration));
        this.typedMessageBodyWriters.add(new ByteArrayBodyHandler());
        this.typedMessageBodyWriters.add(new ByteBodyWriter());
        this.typedMessageBodyWriters.add(new ByteBufferBodyHandler(byteBufferFactory));
        for (TypedMessageBodyHandler<?> otherRawHandler : otherRawHandlers) {
            this.typedMessageBodyReaders.add(otherRawHandler);
            this.typedMessageBodyWriters.add(otherRawHandler);
        }
        add(MediaType.TEXT_PLAIN_TYPE, new TextPlainObjectBodyReader<>(applicationConfiguration, ConversionService.SHARED));
        add(MediaType.TEXT_PLAIN_TYPE, new TextPlainObjectBodyWriter());
    }

    /**
     * Add a {@link MessageBodyHandler} for the given media type.
     *
     * @param mediaType The media type the handler applies to
     * @param handler   The handler
     */
    public void add(MediaType mediaType, MessageBodyHandler<?> handler) {
        writerEntries.add(new WriterEntry(handler, mediaType));
        readerEntries.add(new ReaderEntry(handler, mediaType));
    }

    /**
     * Add a {@link MessageBodyWriter} for the given media type.
     *
     * @param mediaType The media type the handler applies to
     * @param handler   The handler
     */
    public void add(MediaType mediaType, MessageBodyWriter<?> handler) {
        writerEntries.add(new WriterEntry(handler, mediaType));
    }

    /**
     * Add a {@link MessageBodyReader} for the given media type.
     *
     * @param mediaType The media type the handler applies to
     * @param handler   The handler
     */
    public void add(MediaType mediaType, MessageBodyReader<?> handler) {
        readerEntries.add(new ReaderEntry(handler, mediaType));
    }

    @Override
    protected <T> @org.jspecify.annotations.Nullable MessageBodyReader<T> findReaderImpl(Argument<T> type, @org.jspecify.annotations.Nullable List<MediaType> mediaTypes) {
        for (TypedMessageBodyReader<?> messageBodyReader : typedMessageBodyReaders) {
            @SuppressWarnings("unchecked")
            TypedMessageBodyReader<T> reader = (TypedMessageBodyReader<T>) messageBodyReader;
            if (type.getType().isAssignableFrom(reader.getType().getType())) {
                if (mediaTypes == null || mediaTypes.isEmpty()) {
                    if (reader.isReadable(type, null)) {
                        return reader;
                    }
                } else {
                    for (MediaType mt : mediaTypes) {
                        if (reader.isReadable(type, mt)) {
                            return reader;
                        }
                    }
                }
            }
        }
        if (mediaTypes != null) {
            for (MediaType mediaType : mediaTypes) {
                for (ReaderEntry entry : readerEntries) {
                    if (mediaType.matches(entry.mediaType)) {
                        @SuppressWarnings("unchecked")
                        MessageBodyReader<T> h = (MessageBodyReader<T>) entry.handler;
                        return h;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected <T> @org.jspecify.annotations.Nullable MessageBodyWriter<T> findWriterImpl(Argument<T> type, @org.jspecify.annotations.Nullable List<MediaType> mediaTypes) {
        for (TypedMessageBodyWriter<?> messageBodyReader : typedMessageBodyWriters) {
            @SuppressWarnings("unchecked")
            TypedMessageBodyWriter<T> writer = (TypedMessageBodyWriter<T>) messageBodyReader;
            if (messageBodyReader.getType().isAssignableFrom(type.getType())) {
                if (mediaTypes == null || mediaTypes.isEmpty()) {
                    if (writer.isWriteable(type, null)) {
                        return (MessageBodyWriter<T>) messageBodyReader;
                    }
                } else {
                    for (MediaType mt : mediaTypes) {
                        if (writer.isWriteable(type, mt)) {
                            return (MessageBodyWriter<T>) messageBodyReader;
                        }
                    }
                }
            }
        }
        if (mediaTypes != null) {
            for (MediaType mediaType : mediaTypes) {
                for (WriterEntry entry : writerEntries) {
                    if (mediaType.matches(entry.mediaType)) {
                        return (MessageBodyWriter<T>) entry.handler;
                    }
                }
            }
        }
        return null;
    }

    private record ReaderEntry(MessageBodyReader<?> handler, MediaType mediaType) {
    }

    private record WriterEntry(MessageBodyWriter<?> handler, MediaType mediaType) {
    }
}

