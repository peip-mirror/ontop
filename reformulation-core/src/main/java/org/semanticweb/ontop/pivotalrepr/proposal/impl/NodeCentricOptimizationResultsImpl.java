package org.semanticweb.ontop.pivotalrepr.proposal.impl;

import com.google.common.base.Optional;
import org.semanticweb.ontop.pivotalrepr.IntermediateQuery;
import org.semanticweb.ontop.pivotalrepr.QueryNode;
import org.semanticweb.ontop.pivotalrepr.proposal.NodeCentricOptimizationResults;

public class NodeCentricOptimizationResultsImpl<N extends QueryNode> extends ProposalResultsImpl
        implements NodeCentricOptimizationResults<N> {

    private final Optional<QueryNode> optionalNextSibling;
    private final Optional<N> optionalNewNode;
    private final Optional<QueryNode> optionalClosestAncestor;
    private final Optional<QueryNode> optionalReplacingChild;

    public NodeCentricOptimizationResultsImpl(IntermediateQuery query,
                                              N newNode) {
        super(query);
        this.optionalNextSibling = query.getNextSibling(newNode);
        this.optionalNewNode = Optional.of(newNode);
        this.optionalClosestAncestor = query.getParent(newNode);
        this.optionalReplacingChild = Optional.absent();
    }

    /**
     * When the focus node has been removed and not declared as been replaced by its first child.
     *
     */
    public NodeCentricOptimizationResultsImpl(IntermediateQuery query,
                                              Optional<QueryNode> optionalNextSibling,
                                              Optional<QueryNode> optionalClosestAncestor) {
        super(query);
        this.optionalNextSibling = optionalNextSibling;
        this.optionalNewNode = Optional.absent();
        this.optionalClosestAncestor = optionalClosestAncestor;
        this.optionalReplacingChild = Optional.absent();

        /**
         * Checks the closest ancestor is the parent of the next sibling
         * (if any of course).
         */
        if (optionalNextSibling.isPresent() && optionalClosestAncestor.isPresent()) {
            Optional<QueryNode> optionalSiblingParent = query.getParent(optionalNextSibling.get());
            if ((!optionalSiblingParent.isPresent()) || (optionalSiblingParent.get() != optionalClosestAncestor.get())) {
                throw new IllegalArgumentException("The closest ancestor is not the parent of the next sibling");
            }
        }

    }

    /**
     * The replacing child IS NOT OPTIONAL (Optional is here just to avoid confusion with other constructors).
     * TODO: should we refactor it?
     */
    public NodeCentricOptimizationResultsImpl(IntermediateQuery query, Optional<QueryNode> optionalReplacingChild) {
        super(query);
        this.optionalReplacingChild = optionalReplacingChild;
        this.optionalNextSibling = Optional.absent();
        this.optionalNewNode = Optional.absent();
        this.optionalClosestAncestor = Optional.absent();

        if (!optionalReplacingChild.isPresent()) {
            throw new IllegalArgumentException("A replacing child must be given (not optional in practise)");
        }
    }

    /**
     * If absent, it means that the focus node has been deleted.
     */
    @Override
    public Optional<N> getOptionalNewNode() {
        return optionalNewNode;
    }

    /**
     * Is or was (if the node has been deleted) a sibling.
     *
     * Note that in case of cascade deletion, the sibling may appear higher in the tree.
     */
    @Override
    public Optional<QueryNode> getOptionalNextSibling() {
        return optionalNextSibling;
    }

    /**
     * Is usually the parent but may be another ancestor in
     * case of delete cascading.
     */
    @Override
    public Optional<QueryNode> getOptionalClosestAncestor() {
        return optionalClosestAncestor;
    }

    @Override
    public Optional<QueryNode> getOptionalReplacingChild() {
        return optionalReplacingChild;
    }
}