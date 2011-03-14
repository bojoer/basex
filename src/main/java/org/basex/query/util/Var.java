package org.basex.query.util;

import static org.basex.query.QueryTokens.*;
import static org.basex.query.util.Err.*;
import java.io.IOException;
import org.basex.data.Serializer;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.expr.Expr;
import org.basex.query.expr.ParseExpr;
import org.basex.query.item.QNm;
import org.basex.query.item.SeqType;
import org.basex.query.item.Value;
import org.basex.query.iter.Iter;
import org.basex.util.InputInfo;
import org.basex.util.Token;
import org.basex.util.TokenBuilder;

/**
 * Variable expression.
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 */
public final class Var extends ParseExpr {
  /** Expected return type. */
  public SeqType ret = SeqType.ITEM_ZM;
  /** Variable name. */
  public final QNm name;
  /** Global flag. */
  public boolean global;
  /** Declaration flag. */
  public boolean declared;
  /** Variable expressions. */
  private Expr expr;
  /** Variable results. */
  public Value value;

  /** Variable ID. */
  public final int id;

  /**
   * Constructor.
   * @param ii input info
   * @param n variable name
   * @param t data type
   * @param i variable ID
   */
  private Var(final InputInfo ii, final QNm n, final SeqType t, final int i) {
    super(ii);
    name = n;
    type = t;
    id = i;
  }

  /**
   * Creates a new variable.
   * @param ctx query context
   * @param ii input info
   * @param n variable name
   * @param t type
   * @return variable
   */
  public static Var create(final QueryContext ctx, final InputInfo ii,
      final QNm n, final SeqType t) {
    return new Var(ii, n, t, ctx.nextVarID());
  }

  /**
   * Creates a new variable.
   * @param ctx query context
   * @param ii input info
   * @param n variable name
   * @return variable
   */
  public static Var create(final QueryContext ctx, final InputInfo ii,
      final QNm n) {
    return new Var(ii, n, null, ctx.nextVarID());
  }

  /**
   * Checks if all functions have been correctly declared, and initializes
   * all function calls.
   * @throws QueryException query exception
   */
  public void check() throws QueryException {
    if(expr != null && expr.uses(Use.UPD)) UPNOT.thrw(input, desc());
  }

  @Override
  public Var comp(final QueryContext ctx) throws QueryException {
    if(expr != null) bind(checkUp(expr, ctx).comp(ctx), ctx);
    return this;
  }

  /**
   * Binds the specified expression to the variable.
   * @param e expression to be set
   * @param ctx query context
   * @return self reference
   * @throws QueryException query exception
   */
  public Var bind(final Expr e, final QueryContext ctx) throws QueryException {
    expr = e;
    return e.value() ? bind((Value) e, ctx) : this;
  }

  /**
   * Returns the bound expression.
   * @return expression
   */
  public Expr expr() {
    return expr;
  }

  /**
   * Binds the specified value to the variable.
   * @param v value to be set
   * @param ctx query context
   * @return self reference
   * @throws QueryException query exception
   */
  public Var bind(final Value v, final QueryContext ctx) throws QueryException {
    expr = v;
    value = cast(v, ctx);
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) throws QueryException {
    return value(ctx).iter(ctx);
  }

  @Override
  public Value value(final QueryContext ctx) throws QueryException {
    if(value == null) {
      if(expr == null) VAREMPTY.thrw(input, this);
      final Value v = ctx.value;
      ctx.value = null;
      value = cast(expr.comp(ctx).value(ctx), ctx);
      ctx.value = v;
    }
    return value;
  }

  /**
   * Compares the variables for reference or name equality, this should only be
   * used while parsing because it ignores variable IDs.
   * @param v variable
   * @return result of check
   */
  public boolean namedLike(final Var v) {
    return v == this || v.name.eq(name);
  }

  /**
   * Checks whether the given variable is identical to this one, i.e. hast the
   * same ID.
   * @param v variable to check
   * @return {@code true}, if the IDs are equal, {@code false} otherwise
   */
  public boolean is(final Var v) {
    return id == v.id;
  }

  /**
   * If necessary, casts the specified value if a type is specified.
   * @param v input value
   * @param ctx query context
   * @return cast value
   * @throws QueryException query exception
   */
  private Value cast(final Value v, final QueryContext ctx)
      throws QueryException {
    return type == null ? v : type.cast(v, ctx, input);
  }

  @Override
  public Var copy() {
    final Var v = new Var(input, name, type, id);
    v.global = global;
    v.value = value;
    v.expr = expr;
    v.ret = ret;
    return v;
  }

  @Override
  public boolean uses(final Use u) {
    return u == Use.VAR;
  }

  @Override
  public int count(final Var v) {
    return is(v) ? 1 : 0;
  }

  @Override
  public boolean removable(final Var v) {
    // only VarRefs can be removed
    return false;
  }

  @Override
  public Var remove(final Var v) {
    return this;
  }

  @Override
  public SeqType type() {
    return type != null ? type : expr != null ? expr.type() : ret;
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this, NAM, Token.token(toString()));
    if(expr != null) expr.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    final TokenBuilder tb = new TokenBuilder();
    if(name != null) {
      tb.add(DOLLAR).add(name.atom()).addExt("{%}", id);
      if(type != null) tb.add(" " + AS);
    }
    if(type != null) tb.add(" " + type);
    return tb.toString();
  }
}
