// This is a generated file. Not intended for manual editing.
package software.amazon.smithy.intellij.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static software.amazon.smithy.intellij.psi.SmithyTypes.*;
import software.amazon.smithy.intellij.psi.*;
import software.amazon.smithy.intellij.ext.SmithyPsiImplUtilKt;
import com.intellij.openapi.util.NlsSafe;
import software.amazon.smithy.intellij.SmithyShapeReference;

public class SmithyShapeIdImpl extends SmithyPrimitiveImpl implements SmithyShapeId {

  public SmithyShapeIdImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull SmithyVisitor visitor) {
    visitor.visitShapeId(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof SmithyVisitor) accept((SmithyVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public SmithyNamespaceId getNamespaceId() {
    return findChildByClass(SmithyNamespaceId.class);
  }

  @Override
  @NotNull
  public String getId() {
    return SmithyPsiImplUtilKt.getId(this);
  }

  @Override
  @NotNull
  public String getShapeName() {
    return SmithyPsiImplUtilKt.getShapeName(this);
  }

  @Override
  @Nullable
  public String getDeclaredNamespace() {
    return SmithyPsiImplUtilKt.getDeclaredNamespace(this);
  }

  @Override
  @NotNull
  public String getEnclosingNamespace() {
    return SmithyPsiImplUtilKt.getEnclosingNamespace(this);
  }

  @Override
  @NotNull
  public SmithyShapeReference getReference() {
    return SmithyPsiImplUtilKt.getReference(this);
  }

  @Override
  public @NlsSafe String toString() {
    return SmithyPsiImplUtilKt.toString(this);
  }

}
