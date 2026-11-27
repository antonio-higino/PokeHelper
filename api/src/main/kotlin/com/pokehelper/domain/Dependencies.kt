package com.pokehelper.domain

sealed class DependencyFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class DependencyUnavailable(message: String, cause: Throwable? = null) : DependencyFailure(message, cause)

class DependencyTimeout(message: String, cause: Throwable? = null) : DependencyFailure(message, cause)

class DependencyRejected(message: String, cause: Throwable? = null) : DependencyFailure(message, cause)

class CatalogEntryNotFound(message: String) : RuntimeException(message)
