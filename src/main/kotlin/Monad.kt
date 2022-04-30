

interface Monad<F> {

    fun <A> pure(value: A): Kind<F, A>

    fun <A, B> Kind<F, A>.flatMap(f: (A) -> Kind<F, B>): Kind<F, B>
}