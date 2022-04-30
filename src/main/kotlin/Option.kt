
sealed class Option<out A>: OptionOf<A>{
    companion object
}
data class Some<A>(val value: A): Option<A>()
object None: Option<Nothing>()

fun Option.Companion.functor() = object : Functor<ForOption> {
    override fun <A, B> Kind<ForOption, A>.map(f: (A) -> B): Kind<ForOption, B> =
        when(val v = this.fix()) {
            is Some -> Some(f(v.value))
            is None -> None
        }
}

fun Option.Companion.monad() = object : Monad<ForOption> {
    override fun <A, B> Kind<ForOption, A>.flatMap(f: (A) -> Kind<ForOption, B>): Kind<ForOption, B> =
        when(val v = this.fix()) {
            is Some -> f(v.value)
            None -> None
        }

    override fun <A> pure(value: A): Kind<ForOption, A> = Some(value)

}

class ForOption private constructor() { companion object }

typealias OptionOf<A> = Kind<ForOption, A>

inline fun <A> OptionOf<A>.fix(): Option<A> = this as Option<A>