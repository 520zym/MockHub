package com.mockhub.mock.lifecycle;
import java.util.function.Supplier;
/** Trusted server entrypoint attribution, never accepted from a DTO. Nested domain calls retain the outer source. */
public final class ApiMutationSource {
    private static final ThreadLocal<String> SOURCE=new ThreadLocal<String>();
    private ApiMutationSource() { }
    public static String effective(String fallback) { return SOURCE.get()==null?fallback:SOURCE.get(); }
    public static <T> T withSource(String source,Supplier<T> work) {
        String previous=SOURCE.get();SOURCE.set(source);
        try { return work.get(); } finally { if(previous==null)SOURCE.remove();else SOURCE.set(previous); }
    }
}
