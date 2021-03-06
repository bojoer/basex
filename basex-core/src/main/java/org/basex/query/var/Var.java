package org.basex.query.var;

import org.basex.data.ExprInfo;
import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.query.expr.*;
import org.basex.query.util.*;
import org.basex.util.*;

/**
 * Variable expression.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 * @author Leo Woerteler
 */
public final class Var extends ExprInfo {
  /** Variable name. */
  public final QNm name;
  /** Variable ID. */
  public final int id;
  /** Declared type, {@code null} if not specified. */
  public SeqType declType;

  /** Stack slot number. */
  public int slot = -1;
  /** Expected result size. */
  public long size = -1;

  /** Flag for function parameters. */
  private final boolean param;
  /** Actual return type (by type inference). */
  private SeqType inType;
  /** Flag for function conversion. */
  private boolean promote;

  /**
   * Constructor.
   * @param ctx query context, used for generating a variable ID
   * @param n variable name, {@code null} for unnamed variable
   * @param typ expected type, {@code null} for no check
   * @param fun function parameter flag
   */
  Var(final QueryContext ctx, final QNm n, final SeqType typ, final boolean fun) {
    name = n;
    declType = typ;
    inType = SeqType.ITEM_ZM;
    id = ctx.varIDs++;
    param = fun;
    promote = fun;
    size = inType.occ();
  }

  /**
   * Constructor for local variables.
   * @param ctx query context, used for generating a variable ID
   * @param n variable name, {@code null} for unnamed variable
   * @param typ expected type, {@code null} for no check
   */
  Var(final QueryContext ctx, final QNm n, final SeqType typ) {
    this(ctx, n, typ, false);
  }

  /**
   * Copy constructor.
   * @param ctx query context
   * @param var variable to copy
   */
  Var(final QueryContext ctx, final Var var) {
    this(ctx, var.name, var.declType, var.param);
    promote = var.promote;
    inType = var.inType;
    size = var.size;
  }

  /**
   * Type of values bound to this variable.
   * @return (non-{@code null}) type
   */
  public SeqType type() {
    final SeqType intersect = declType != null ? declType.intersect(inType) : null;
    return intersect != null ? intersect : declType != null ? declType : inType;
  }

  /**
   * Declared type of this variable.
   * @return declared type, possibly {@code null}
   */
  public SeqType declaredType() {
    return declType == null ? SeqType.ITEM_ZM : declType;
  }

  /**
   * Tries to refine the compile-time type of this variable through the type of the bound
   * expression.
   * @param t type of the bound expression
   * @param ctx query context
   * @param ii input info
   * @throws QueryException query exception
   */
  public void refineType(final SeqType t, final QueryContext ctx, final InputInfo ii)
      throws QueryException {

    if(t == null) return;
    if(declType != null) {
      if(declType.occ.intersect(t.occ) == null) Err.INVCAST.thrw(ii, t, declType);
      if(!t.convertibleTo(declType)) return;
    }

    if(!inType.eq(t) && !inType.instanceOf(t)) {
      final SeqType is = inType.intersect(t);
      if(is != null) {
        inType = is;
        if(declType != null && inType.instanceOf(declType)) {
          ctx.compInfo(QueryText.OPTCAST, this);
          declType = null;
        }
      }
    }
  }

  /**
   * Determines if this variable checks the type of the expression bound to it.
   * @return {@code true} if the type is checked or promoted, {@code false} otherwise
   */
  public boolean checksType() {
    return declType != null;
  }

  /**
   * Returns an equivalent to the given expression that checks this variable's type.
   * @param e expression
   * @param scp variable scope
   * @param ctx query context
   * @param ii input info
   * @return checked expression
   * @throws QueryException query exception
   */
  public Expr checked(final Expr e, final QueryContext ctx, final VarScope scp,
      final InputInfo ii) throws QueryException {
    return checksType()
        ? new TypeCheck(ii, e, declType, promotes()).optimize(ctx, scp) : e;
  }

  /**
   * Checks the type of this value and casts/promotes it when necessary.
   * @param val value to be checked
   * @param ctx query context
   * @param ii input info
   * @return checked and possibly cast value
   * @throws QueryException if the check failed
   */
  public Value checkType(final Value val, final QueryContext ctx, final InputInfo ii)
      throws QueryException {
    if(!checksType() || declType.instance(val)) return val;
    if(promote) return declType.funcConvert(ctx, ii, val);
    throw Err.INVCAST.thrw(ii, val.type(), declType);
  }

  /**
   * Checks whether the given variable is identical to this one, i.e. has the
   * same ID.
   * @param v variable to check
   * @return {@code true}, if the IDs are equal, {@code false} otherwise
   */
  public boolean is(final Var v) {
    return id == v.id;
  }

  /**
   * Checks if this variable performs function conversion on its bound values.
   * @return result of check
   */
  public boolean promotes() {
    return promote;
  }

  @Override
  public void plan(final FElem plan) {
    final FElem e = planElem(QueryText.NAM, '$' + Token.string(name.string()),
        QueryText.ID, Token.token(id));
    if(declType != null) e.add(planAttr(QueryText.AS, declType.toString()));
    addPlan(plan, e);
  }

  @Override
  public String toString() {
    final TokenBuilder tb = new TokenBuilder();
    if(name != null) {
      tb.add(QueryText.DOLLAR).add(name.string());
      if(declType != null) tb.add(' ' + QueryText.AS);
    }
    if(declType != null) tb.add(" " + declType);
    return tb.toString();
  }

  @Override
  public boolean equals(final Object obj) {
    return obj instanceof Var && is((Var) obj);
  }

  @Override
  public int hashCode() {
    return id;
  }

  /**
   * Tries to adopt the given type check.
   * @param t type to check
   * @param prom if function conversion should be applied
   * @return {@code true} if the check could be adopted, {@code false} otherwise
   */
  public boolean adoptCheck(final SeqType t, final boolean prom) {
    if(declType == null || t.instanceOf(declType)) {
      declType = t;
    } else if(!declType.instanceOf(t)) {
      return false;
    }

    promote |= prom;
    return true;
  }
}