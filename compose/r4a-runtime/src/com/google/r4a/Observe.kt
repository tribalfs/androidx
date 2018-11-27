package com.google.r4a

@Composable
fun Observe(@Children body: @Composable() () -> Unit) =
    (CompositionContext.current as? ComposerCompositionContext)?.composer?.let { composer ->
        composer.startJoin(false) { body() }
        body()
        composer.doneJoin(false)
    } ?: body()
