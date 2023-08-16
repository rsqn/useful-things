package tech.rsqn.useful.things.lambda;

import tech.rsqn.useful.things.lambda.model.HttpRequestDto;

public abstract class AbstractHttpFunction<C, R> {
    public abstract R handle(HttpRequestDto dto, C c);

    public Class getModel() {
        return null;
    }
}
