/**
 * Glin, A retrofit like network framework <br />
 *
 * Usage: <br />
 *  1. write your client and parser, config glin
 *      Glin glin = new Glin.Builder()
             .client(new OkClient())
             .baseUrl("http://192.168.201.39")
             .debug(true)
             .parserFactory(new FastJsonParserFactory())
             .timeout(10000)
             .build();
 *
 *  2. create an interface
 *      public interface UserBiz {
            @POST("/users/list")
            Call<User> list(@Arg("name") String userName);
        }
 *
 *  3. request the network and callback
 *      UserBiz biz = glin.create(UserBiz.class, getClass().getName());
 *      Call<User> call = biz.list("qibin");
        call.enqueue(new Callback<User>() {
            @Override
            public void onResponse(Result<User> result) {
                if (result.isOK()) {
                    Toast.makeText(MainActivity.this, result.getResult().getName(), Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(MainActivity.this, result.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
 */
package org.loader.glin;

import org.loader.glin.annotation.Arg;
import org.loader.glin.annotation.JSON;
import org.loader.glin.annotation.POST;
import org.loader.glin.call.Call;
import org.loader.glin.call.CallMapping;
import org.loader.glin.client.IClient;
import org.loader.glin.parser.ParserFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by qibin on 2016/7/13.
 */

public class Glin {
    private IClient mClient;
    private String mBaseUrl;

    private CallMapping mCallMapping;

    private Glin(IClient client, String baseUrl) {
        mClient = client;
        mBaseUrl = baseUrl;
        mCallMapping = new CallMapping();
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> klass, Object tag) {
        return (T) Proxy.newProxyInstance(klass.getClassLoader(),
                new Class<?>[] {klass}, new Handler(tag));
    }

    class Handler implements InvocationHandler {
        private Object mTag;

        public Handler(Object tag) {
            mTag = tag;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String url = mBaseUrl == null ? "" : mBaseUrl;
            Class<? extends Annotation> key = null;

            if (method.isAnnotationPresent(JSON.class)) {
                if(!method.isAnnotationPresent(POST.class)) {
                    throw new UnsupportedOperationException("cannot find POST annotation");
                }
                key = JSON.class;
                url += method.getAnnotation(POST.class).value();
            } else {
                HashMap<Class<? extends Annotation>, Class<? extends Call>> mapping = mCallMapping.get();
                Class<? extends Annotation> item;
                Annotation anno;
                for(Iterator<Class<? extends Annotation>> iterator = mapping.keySet().iterator();
                    iterator.hasNext();) {
                    item = iterator.next();
                    if (method.isAnnotationPresent(item)) {
                        key = item;
                        anno = method.getAnnotation(item);
                        url += (String) anno.getClass().getDeclaredMethod("value").invoke(anno);
                        break;
                    }
                }
            }

            if (key == null) {
                throw new UnsupportedOperationException("cannot find annotations");
            }

            Class<? extends Call> callKlass = mCallMapping.get(key);
            if (callKlass == null) {
                throw new UnsupportedOperationException("cannot find calls");
            }

            Constructor<? extends Call> constructor = callKlass.getConstructor(IClient.class, String.class, Params.class, Object.class);
            Call<?> call = constructor.newInstance(mClient, url, params(method, args), mTag);
            if (call == null) {
                throw new UnsupportedOperationException("cannot find calls");
            }

            return call;
        }

        private Params params(Method method, Object[] args) {
            Params params = new Params();

            if (args == null || args.length == 0) {
                return params;
            }

            if (method.isAnnotationPresent(JSON.class)) {
                params.add(Params.DEFAULT_JSON_KEY, args[0]);
                return params;
            }

            Annotation[][] paramsAnno = method.getParameterAnnotations();
            if (paramsAnno.length != args.length) {
                throw new UnsupportedOperationException("args must be annotated");
            }

            int length = paramsAnno.length;
            for (int i = 0; i < length; i++) {
                params.add(((Arg)paramsAnno[i][0]).value(), args[i]);
            }

            return params;
        }
    }

    public static class Builder {
        private IClient mClient;
        private String mBaseUrl;

        public Builder() {

        }

        public Builder baseUrl(String baseUrl) {
            mBaseUrl = baseUrl;
            return this;
        }

        public Builder client(IClient client) {
            mClient = client;
            return this;
        }

        public Builder parserFactory(ParserFactory factory) {
            if (mClient == null) {
                throw new UnsupportedOperationException("invoke client method first");
            }
            mClient.parserFactory(factory);
            return this;
        }

        public Builder timeout(long ms) {
            if (mClient == null) {
                throw new UnsupportedOperationException("invoke client method first");
            }

            return this;
        }

        public Builder debug(boolean debugMode) {
            if (mClient == null) {
                throw new UnsupportedOperationException("invoke client method first");
            }
            mClient.debugMode(debugMode);
            return this;
        }

        public Glin build() {
            return new Glin(mClient, mBaseUrl);
        }
    }
}
