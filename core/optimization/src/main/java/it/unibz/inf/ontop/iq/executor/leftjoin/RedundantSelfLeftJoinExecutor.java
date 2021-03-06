package it.unibz.inf.ontop.iq.executor.leftjoin;

import com.google.common.collect.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unibz.inf.ontop.dbschema.DBMetadata;
import it.unibz.inf.ontop.iq.executor.SimpleNodeCentricExecutor;
import it.unibz.inf.ontop.iq.executor.join.SelfJoinLikeExecutor;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.exception.EmptyQueryException;
import it.unibz.inf.ontop.iq.exception.InvalidQueryOptimizationProposalException;
import it.unibz.inf.ontop.iq.node.DataNode;
import it.unibz.inf.ontop.iq.node.EmptyNode;
import it.unibz.inf.ontop.iq.node.LeftJoinNode;
import it.unibz.inf.ontop.iq.node.QueryNode;
import it.unibz.inf.ontop.iq.*;
import it.unibz.inf.ontop.iq.impl.QueryTreeComponent;
import it.unibz.inf.ontop.iq.proposal.*;
import it.unibz.inf.ontop.iq.proposal.impl.NodeCentricOptimizationResultsImpl;
import it.unibz.inf.ontop.iq.proposal.impl.RemoveEmptyNodeProposalImpl;
import it.unibz.inf.ontop.model.atom.AtomPredicate;
import it.unibz.inf.ontop.model.term.GroundTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.term.VariableOrGroundTerm;

import java.util.*;

import static it.unibz.inf.ontop.iq.executor.leftjoin.RedundantSelfLeftJoinExecutor.Action.DO_NOTHING;
import static it.unibz.inf.ontop.iq.executor.leftjoin.RedundantSelfLeftJoinExecutor.Action.DROP_RIGHT;
import static it.unibz.inf.ontop.iq.executor.leftjoin.RedundantSelfLeftJoinExecutor.Action.UNIFY;
import static it.unibz.inf.ontop.iq.node.BinaryOrderedOperatorNode.ArgumentPosition.LEFT;
import static it.unibz.inf.ontop.iq.node.BinaryOrderedOperatorNode.ArgumentPosition.RIGHT;

/**
 * TODO: explain
 *
 * Assumption: clean inner join structure (an inner join does not have another inner join or filter node as a child).
 *
 * Naturally assumes that the data atoms are leafs.
 *
 */
@Singleton
public class RedundantSelfLeftJoinExecutor
        extends SelfJoinLikeExecutor
        implements SimpleNodeCentricExecutor<LeftJoinNode, LeftJoinOptimizationProposal> {

    private final IntermediateQueryFactory iqFactory;

    enum Action {
        UNIFY, DO_NOTHING, DROP_RIGHT
    }

    @Inject
    private RedundantSelfLeftJoinExecutor(IntermediateQueryFactory iqFactory) {
        this.iqFactory = iqFactory;
    }

    @Override
    public NodeCentricOptimizationResults<LeftJoinNode>
    apply(LeftJoinOptimizationProposal proposal, IntermediateQuery query, QueryTreeComponent treeComponent)
            throws InvalidQueryOptimizationProposalException, EmptyQueryException {

        LeftJoinNode leftJoinNode = proposal.getFocusNode();

        QueryNode leftChild = query.getChild(leftJoinNode,LEFT)
                .orElseThrow(() -> new IllegalStateException("The left child of a LJ is missing: " + leftJoinNode ));
        QueryNode rightChild = query.getChild(leftJoinNode,RIGHT)
                .orElseThrow(() -> new IllegalStateException("The right child of a LJ is missing: " + leftJoinNode));

        if (leftChild instanceof DataNode && rightChild instanceof DataNode) {

            DataNode leftDataNode = (DataNode) leftChild;
            DataNode rightDataNode = (DataNode) rightChild;

            if (isOptimizableSelfLeftJoin(leftDataNode, rightDataNode, query.getDBMetadata())) {
                return tryToOptimizeSelfJoin(leftDataNode, rightDataNode, query, treeComponent, leftJoinNode);
            }
        }

        // No optimization
        return new NodeCentricOptimizationResultsImpl<>(query, leftJoinNode);
    }

    /**
     * Checks if we are dealing with optimizable self left join, i.e.,
     * the left and the right predicates are the same, and
     * the join is over the keys
     */
    private boolean isOptimizableSelfLeftJoin(DataNode leftDataNode, DataNode rightDataNode, DBMetadata metadata) {
        AtomPredicate leftPredicate = leftDataNode.getProjectionAtom().getPredicate();
        AtomPredicate rightPredicate = rightDataNode.getProjectionAtom().getPredicate();

        if(leftPredicate.equals(rightPredicate)) {
            if(metadata.getUniqueConstraints().containsKey(leftPredicate)) {
                ImmutableMultimap<ImmutableList<VariableOrGroundTerm>, DataNode> groupingMap =
                        groupByPrimaryKeyArguments(leftDataNode, rightDataNode,
                                metadata.getUniqueConstraints().get(leftDataNode.getProjectionAtom().getPredicate()));

                for(ImmutableList<VariableOrGroundTerm> variables: groupingMap.keySet()) {
                    /**
                     * At least for one unique constraint, the left and the right data nodes
                     * join on the key positions. Hence, it is an optimizable self join.
                     */
                    if(groupingMap.get(variables).size() == 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private NodeCentricOptimizationResults<LeftJoinNode> tryToOptimizeSelfJoin(DataNode leftDataNode, DataNode rightDataNode,
                                                                               IntermediateQuery query,
                                                                               QueryTreeComponent treeComponent,
                                                                               LeftJoinNode leftJoinNode)
            throws EmptyQueryException {

        /**
         * No optimization if a left join condition is present
         */
        if(leftJoinNode.getOptionalFilterCondition().isPresent()) {
            return new NodeCentricOptimizationResultsImpl<>(query, leftJoinNode);
        }

        /**
         * No optimization if there are implicit equalities between terms at different positions
         */
        if(containsImplicitEqualities(leftDataNode, rightDataNode)) {
            return new NodeCentricOptimizationResultsImpl<>(query, leftJoinNode);
        }

        /**
         * There exists a valid substitution from the rightDataNode to the leftDataNode. Hence, we can
         * get rid of the rightDataNode as in the inner join case.
         */

        Action action = existsSubstitutionFromRightToLeft(leftDataNode, rightDataNode);
        switch (action) {
            case UNIFY:
                /**
                 * Unify similarly to the inner join case
                 */
                return tryToUnify(leftDataNode, rightDataNode, query, treeComponent, leftJoinNode);

            case DO_NOTHING:
                /**
                 * No optimization
                 */
                return new NodeCentricOptimizationResultsImpl<>(query, leftJoinNode);

            case DROP_RIGHT:
                /**
                 * LeftJoin never joins the left part with the right one,
                 * so we remove the right node.
                 */
                return tryToDropRight(leftJoinNode, rightDataNode, query, treeComponent);

            default:
                throw new IllegalStateException("Unexpected action " + action);
        }
    }

    /**
     * For two optimizable data nodes with the same predicate, we perform
     * a simple check on whether there are implicit equalities
     * between variables at different positions.
     *
     * E.g., this method detects that R(x1,x2,x3,x4) and R(x1,x3,x5,x6) contain an e
     * xplicit inequality given by the variable x3.
     *
     * But it does not detect in this case: R(x1,x2,x3,x4) and R(x1,x5,x5,x6).
     */
    private boolean containsImplicitEqualities(DataNode leftDataNode, DataNode rightDataNode) {

        int n=leftDataNode.getProjectionAtom().getEffectiveArity();
        for(int i=0; i<n; i++) {
            VariableOrGroundTerm leftTerm = leftDataNode.getProjectionAtom().getTerm(i);
            if(leftTerm instanceof GroundTerm)
                continue;

            for(int j=0; j<n; j++) {
                if(i != j) {
                    VariableOrGroundTerm rightTerm = rightDataNode.getProjectionAtom().getTerm(j);
                    if(leftTerm.equals(rightTerm)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * Tries to unify the right node with the left node (hence,
     * get rid of the left join).
     *
     * Essentially does the same thing as for the self join optimization.
     */
    private NodeCentricOptimizationResults<LeftJoinNode> tryToUnify(
            DataNode leftDataNode,
            DataNode rightDataNode,
            IntermediateQuery query,
            QueryTreeComponent treeComponent,
            LeftJoinNode leftJoinNode) throws EmptyQueryException {

        ImmutableMultimap<ImmutableList<VariableOrGroundTerm>, DataNode> groupingMap =
                groupByPrimaryKeyArguments(leftDataNode, rightDataNode,
                        query.getDBMetadata().getUniqueConstraints().get(leftDataNode.getProjectionAtom().getPredicate()));

        ImmutableList<Variable> priorityVariables = prioritizeVariables(query, leftJoinNode);

        try {
            PredicateLevelProposal predicateLevelProposal = proposeForGroupingMap(groupingMap);
            Optional<ConcreteProposal> optionalConcreteProposal = createConcreteProposal(
                    ImmutableList.of(predicateLevelProposal),
                    priorityVariables);
            if (optionalConcreteProposal.isPresent()) {
                ConcreteProposal concreteProposal = optionalConcreteProposal.get();

                // SIDE-EFFECT on the tree component (and thus on the query)
                return applyOptimization(query, treeComponent, leftJoinNode, concreteProposal);
            }
        } catch (AtomUnificationException e) {}

        return new NodeCentricOptimizationResultsImpl<>(query, leftJoinNode);
    }

    /**
     * Tries to drop the right data node.
     *
     * Might result in bottom-up transformation of the query, and
     * even lead to the empty query.
     */
    private NodeCentricOptimizationResults<LeftJoinNode> tryToDropRight(LeftJoinNode leftJoinNode, DataNode rightDataNode, IntermediateQuery query, QueryTreeComponent treeComponent) throws EmptyQueryException {
        EmptyNode emptyChild = iqFactory.createEmptyNode(query.getVariables(rightDataNode));
        treeComponent.replaceSubTree(rightDataNode, emptyChild);

        RemoveEmptyNodeProposal emptyNodeProposal = new RemoveEmptyNodeProposalImpl(emptyChild, true);

        NodeTrackingResults<EmptyNode> removalResults = query.applyProposal(emptyNodeProposal);

        /**
         * Retrieves the status of the parent of the empty node (the LJ node)
         */
        NodeTracker.NodeUpdate<LeftJoinNode> leftJoinUpdate = removalResults.getOptionalTracker()
                .orElseThrow(() -> new IllegalArgumentException("Tracking was required"))
                .getUpdate(query, leftJoinNode);

        Optional<QueryNode> optionalReplacingChild = leftJoinUpdate.getReplacingChild();
        if (optionalReplacingChild.isPresent())
            return new NodeCentricOptimizationResultsImpl<>(query, optionalReplacingChild);
        else if (leftJoinUpdate.getNewNode().isPresent()) {
            return new NodeCentricOptimizationResultsImpl<>(query, leftJoinUpdate.getNewNode().get());
        } else {
            return new NodeCentricOptimizationResultsImpl<>(query,
                    leftJoinUpdate.getOptionalNextSibling(query),
                    leftJoinUpdate.getOptionalClosestAncestor(query));
        }
    }


    /**
     * Checks whether there exists a valid substitution from the right data node
     * to the left data node, and returns a corresponding action.
     *
     * If there exists a valid substitution, returns UNIFY.
     *
     * When a valid substitution does not exist, returns
     * <il>
     *   <li> DROP_RIGHT, when the two data nodes can never be unified (i.e., when "1" has to be unified with "2") </li>
     *   <li> DO_NOTHING, when the two data nodes can be possibly unified, but not in general </li>
     * </il>
     */
    private Action existsSubstitutionFromRightToLeft(DataNode leftDataNode, DataNode rightDataNode) {
        Map<Variable, VariableOrGroundTerm> substitutionProposal = new HashMap<>();

        /**
         * Next, we check whether there are no implicit equalities derived from other
         * joining variables, not on the primary key positions
         */
        //ImmutableSet<Variable> joiningVariables = getJoiningVariables(leftDataNode, rightDataNode);

        //if(Sets.difference(joiningVariables, ImmutableSet.copyOf(variables)).isEmpty()) {
        //}

        for(int i=0; i< leftDataNode.getProjectionAtom().getEffectiveArity(); i++) {
            VariableOrGroundTerm leftTerm = leftDataNode.getProjectionAtom().getTerm(i);
            VariableOrGroundTerm rightTerm = rightDataNode.getProjectionAtom().getTerm(i);

            if(rightTerm instanceof GroundTerm) {
                if(!rightTerm.equals(leftTerm)) {
                    if(leftTerm instanceof GroundTerm) {
                        /**
                         * Not a valid substitution when we try to map a constant to a different constant.
                         * A left join is impossible, so we can get rid of the right data node.
                         */
                        return DROP_RIGHT;
                    } else {
                        /**
                         * No a valid substitution, but a left join is still possible
                         * under certain conditions. So we cannot optimize the left join, hence do nothing.
                         */
                        return DO_NOTHING;
                    }
                } else {
                    // do nothing
                }
            } else if(substitutionProposal.containsKey(rightTerm)) {
                if( !substitutionProposal.get(rightTerm).equals(leftTerm)) {
                    /**
                     * Not a valid substitution when we try to map a variable to two different terms.
                     * A left join is possible, but we cannot optimize it.
                     */
                    return DO_NOTHING;
                } else {
                    // do nothing
                }
            } else {
                substitutionProposal.put((Variable)rightTerm, leftTerm);
            }
        }

        return UNIFY;
    }

    /**
     * left and right data nodes and collectionOfPrimaryKeyPositions are given for the same predicate
     * Collects the data nodes where a variable on the primary key position occurs.
     */
    private static ImmutableMultimap<ImmutableList<VariableOrGroundTerm>, DataNode> groupByPrimaryKeyArguments(
            DataNode leftDataNode,
            DataNode rightDataNode,
            ImmutableCollection<ImmutableList<Integer>> collectionOfPrimaryKeyPositions) {
        ImmutableMultimap.Builder<ImmutableList<VariableOrGroundTerm>, DataNode> groupingMapBuilder = ImmutableMultimap.builder();

        for (ImmutableList<Integer> primaryKeyPositions : collectionOfPrimaryKeyPositions) {
            groupingMapBuilder.put(extractArguments(leftDataNode.getProjectionAtom(), primaryKeyPositions), leftDataNode);
            groupingMapBuilder.put(extractArguments(rightDataNode.getProjectionAtom(), primaryKeyPositions), rightDataNode);
        }
        return groupingMapBuilder.build();
    }

    private static ImmutableSet<Variable> getJoiningVariables(DataNode leftDataNode, DataNode rightDataNode) {
        Multimap<Variable, DataNode> mapBuilder = ArrayListMultimap.create();
        for (Variable variable : leftDataNode.getVariables()) {
            mapBuilder.put(variable, leftDataNode);
        }
        for (Variable variable : rightDataNode.getVariables()) {
            mapBuilder.put(variable, rightDataNode);
        }

        Set<Variable> joiningVariables = new HashSet<>();
        for(Variable var: mapBuilder.keySet()) {
            if(mapBuilder.get(var).size() > 1) {
                joiningVariables.add(var);
            }
        }
        return ImmutableSet.copyOf(joiningVariables);
    }

    /**
     * Assumes that the data atoms are leafs.
     *
     */
    private NodeCentricOptimizationResults<LeftJoinNode> applyOptimization(IntermediateQuery query,
                                                                           QueryTreeComponent treeComponent,
                                                                           LeftJoinNode leftJoinNode,
                                                                           ConcreteProposal proposal) throws EmptyQueryException {
        /*
         * First, add and remove non-top nodes
         */
        proposal.getDataNodesToRemove()
                .forEach(treeComponent::removeSubTree);

        return updateJoinNodeAndPropagateSubstitution(query, treeComponent, leftJoinNode, proposal);
    }



}
