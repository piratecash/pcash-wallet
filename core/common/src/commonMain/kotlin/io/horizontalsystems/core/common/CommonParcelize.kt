@file:OptIn(ExperimentalMultiplatform::class)

package io.horizontalsystems.core.common

expect interface CommonParcelable

@OptionalExpectation
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
expect annotation class CommonParcelize()

@OptionalExpectation
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
expect annotation class CommonIgnoredOnParcel()
