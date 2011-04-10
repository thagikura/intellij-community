package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptor;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import com.intellij.structuralsearch.equivalence.MultiChildDescriptor;
import com.intellij.structuralsearch.equivalence.SingleChildDescriptor;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.SiblingNodeIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class SkippingHandler extends MatchingHandler implements DelegatingHandler {

  private final MatchingHandler myDelegate;

  public SkippingHandler(@NotNull MatchingHandler delegate) {
    myDelegate = delegate;
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, final MatchContext matchContext) {
    if (patternNode == null || matchedNode == null || matchedNode.getClass() == patternNode.getClass()) {
      return myDelegate.match(patternNode, matchedNode, matchContext);
    }

    /*if (patternNode != null && matchedNode != null && patternNode.getClass() == matchedNode.getClass()) {
      //return myDelegate.match(patternNode, matchedNode, matchContext);
    }*/
    PsiElement newPatternNode = skipNodeIfNeccessary(patternNode);
    matchedNode = skipNodeIfNeccessary(matchedNode);

    if (newPatternNode != patternNode) {
      return matchContext.getPattern().getHandler(newPatternNode).match(newPatternNode, matchedNode, matchContext);
    }

    return myDelegate.match(patternNode, matchedNode, matchContext);
  }

  @Override
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return myDelegate.matchSequentially(nodes, nodes2, context);
  }

  public boolean match(PsiElement patternNode,
                       PsiElement matchedNode,
                       final int start,
                       final int end,
                       final MatchContext context) {
    if (patternNode == null || matchedNode == null || patternNode.getClass() == matchedNode.getClass()) {
      return myDelegate.match(patternNode, matchedNode, start, end, context);
    }

    PsiElement newPatternNode = skipNodeIfNeccessary(patternNode);
    matchedNode = skipNodeIfNeccessary(matchedNode);

    if (newPatternNode != patternNode) {
      return context.getPattern().getHandler(newPatternNode).match(newPatternNode, matchedNode, start, end, context);
    }

    return myDelegate.match(patternNode, matchedNode, start, end, context);
  }

  protected boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return myDelegate.isMatchSequentiallySucceeded(nodes2);
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(PsiElement patternElement, PsiElement matchedElement) {
    return true;
  }

  public MatchingHandler getDelegate() {
    return myDelegate;
  }

  @Nullable
  public static PsiElement getOnlyNonWhitespaceChild(PsiElement element) {
    PsiElement onlyChild = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiWhiteSpace) {
        continue;
      }
      if (onlyChild != null) {
        return null;
      }
      onlyChild = child;
    }
    return onlyChild;
  }

  @Nullable
  public static PsiElement skipNodeIfNeccessary(PsiElement element) {
    if (element == null) {
      return null;
    }

    /*if (!canSkip(element) && getOnlyNonWhitespaceChild(element) == null) {
      return element;
    }*/

    // todo optimize! (this method is often invokated for the same node)

    final PsiElement onlyChild = getOnlyChildFromDescriptor(element);
    if (onlyChild != null) {
      return onlyChild;
    }

    return getOnlyNonLexicalChild(element);
  }

  private static PsiElement getOnlyNonLexicalChild(PsiElement element) {
    FilteringNodeIterator it = new FilteringNodeIterator(new SiblingNodeIterator(element.getFirstChild()));
    PsiElement child = it.current();
    if (child != null) {
      it.advance();
      if (!it.hasNext()) {
        return child;
      }
    }
    return element;
  }

  @Nullable
  private static PsiElement getOnlyChildFromDescriptor(PsiElement element) {
    final EquivalenceDescriptorProvider provider = EquivalenceDescriptorProvider.getInstance(element);
    if (provider == null) {
      return null;
    }

    final EquivalenceDescriptor equivalenceDescriptor = provider.buildDescriptor(element);
    if (equivalenceDescriptor == null) {
      return null;
    }

    if (equivalenceDescriptor.getConstants().size() > 0) {
      return null;
    }

    final List<SingleChildDescriptor> singleChildren = equivalenceDescriptor.getSingleChildDescriptors();
    final List<MultiChildDescriptor> multiChildren = equivalenceDescriptor.getMultiChildDescriptors();

    if (singleChildren.size() + multiChildren.size() != 1) {
      return null;
    }

    if (singleChildren.size() > 0) {
      final SingleChildDescriptor descriptor = singleChildren.get(0);
      final PsiElement child = descriptor.getElement();

      if (child != null) {
        final SingleChildDescriptor.MyType type = descriptor.getType();

        if (type == SingleChildDescriptor.MyType.DEFAULT) {
          return child;
        }
        else if (type == SingleChildDescriptor.MyType.CHILDREN ||
                 type == SingleChildDescriptor.MyType.CHILDREN_IN_ANY_ORDER) {
          return getOnlyNonLexicalChild(child);
        }
      }
    }
    else {
      final MultiChildDescriptor descriptor = multiChildren.get(0);
      final PsiElement[] children = descriptor.getElements();

      if (children != null && children.length == 1 && descriptor.getType() != MultiChildDescriptor.MyType.OPTIONALLY) {
        return children[0];
      }
    }
    return null;
  }
}
