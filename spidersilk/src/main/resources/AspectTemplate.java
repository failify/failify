public aspect {{aspect_name}} {
    pointcut pointcut_{{aspect_name}}() : execution({{instrumentation_point}});
    before() : pointcut_{{aspect_name}}() {
        {{before_instructions}}
    }
    after() : pointcut_{{aspect_name}}() {
        {{after_instructions}}
    }
}