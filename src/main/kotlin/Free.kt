
sealed class Free<out F, out A>: FreeOf<F, A>
data class Pure<F, A>(val value: A): Free<F, A>()
data class Suspend<F, A>(val next: Kind<F, FreeOf<F, A>>): Free<F, A>()

context(Functor<F>)
fun <F, A> Kind<F, A>.liftF(): Free<F, A> =
    Suspend(this.map { Pure(it) })

context(Functor<F>)
fun <F> freeMonad(): Monad<PartialFreeOf<F>> = object: Monad<PartialFreeOf<F>> {
    override fun <A> pure(value: A): Kind<PartialFreeOf<F>, A> = Pure(value)

    override fun <A, B> Kind<PartialFreeOf<F>, A>.flatMap(f: (A) -> Kind<PartialFreeOf<F>, B>): Kind<PartialFreeOf<F>, B> =
        when(val fa = this.fix()) {
            is Pure -> f(fa.value)
            is Suspend -> Suspend(fa.next.map { subFree -> subFree.flatMap(f) })
        }

}

class ForFree { private constructor() {} }

typealias FreeOf<F, A> = Kind<PartialFreeOf<F>, A>
typealias PartialFreeOf<F> = Kind<ForFree, F>

inline fun <F, A> FreeOf<F, A>.fix(): Free<F, A> = this as Free<F, A>


//fun <F, A> Free<F, A>.functor() = object: Functor<PartialFreeOf<F>> {
//    override fun <A, B> Kind<PartialFreeOf<F>, A>.map(f: (A) -> B): Kind<PartialFreeOf<F>, B> {
//        TODO("Not yet implemented")
//    }
//
//}
