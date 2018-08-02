package androidx.ui.rendering.obj

/// An object in the render tree.
///
/// The [RenderObject] class hierarchy is the core of the rendering
/// library's reason for being.
///
/// [RenderObject]s have a [parent], and have a slot called [parentData] in
/// which the parent [RenderObject] can store child-specific data, for example,
/// the child position. The [RenderObject] class also implements the basic
/// layout and paint protocols.
///
/// The [RenderObject] class, however, does not define a child model (e.g.
/// whether a node has zero, one, or more children). It also doesn't define a
/// coordinate system (e.g. whether children are positioned in Cartesian
/// coordinates, in polar coordinates, etc) or a specific layout protocol (e.g.
/// whether the layout is width-in-height-out, or constraint-in-size-out, or
/// whether the parent sets the size and position of the child before or after
/// the child lays out, etc; or indeed whether the children are allowed to read
/// their parent's [parentData] slot).
///
/// The [RenderBox] subclass introduces the opinion that the layout
/// system uses Cartesian coordinates.
///
/// ## Writing a RenderObject subclass
///
/// In most cases, subclassing [RenderObject] itself is overkill, and
/// [RenderBox] would be a better starting point. However, if a render object
/// doesn't want to use a Cartesian coordinate system, then it should indeed
/// inherit from [RenderObject] directly. This allows it to define its own
/// layout protocol by using a new subclass of [Constraints] rather than using
/// [BoxConstraints], and by potentially using an entirely new set of objects
/// and values to represent the result of the output rather than just a [Size].
/// This increased flexibility comes at the cost of not being able to rely on
/// the features of [RenderBox]. For example, [RenderBox] implements an
/// intrinsic sizing protocol that allows you to measure a child without fully
/// laying it out, in such a way that if that child changes size, the parent
/// will be laid out again (to take into account the new dimensions of the
/// child). This is a subtle and bug-prone feature to get right.
///
/// Most aspects of writing a [RenderBox] apply to writing a [RenderObject] as
/// well, and therefore the discussion at [RenderBox] is recommended background
/// reading. The main differences are around layout and hit testing, since those
/// are the aspects that [RenderBox] primarily specializes.
///
/// ### Layout
///
/// A layout protocol begins with a subclass of [Constraints]. See the
/// discussion at [Constraints] for more information on how to write a
/// [Constraints] subclass.
///
/// The [performLayout] method should take the [constraints], and apply them.
/// The output of the layout algorithm is fields set on the object that describe
/// the geometry of the object for the purposes of the parent's layout. For
/// example, with [RenderBox] the output is the [RenderBox.size] field. This
/// output should only be read by the parent if the parent specified
/// `parentUsesSize` as true when calling [layout] on the child.
///
/// Anytime anything changes on a render object that would affect the layout of
/// that object, it should call [markNeedsLayout].
///
/// ### Hit Testing
///
/// Hit testing is even more open-ended than layout. There is no method to
/// override, you are expected to provide one.
///
/// The general behavior of your hit-testing method should be similar to the
/// behavior described for [RenderBox]. The main difference is that the input
/// need not be an [Offset]. You are also allowed to use a different subclass of
/// [HitTestEntry] when adding entries to the [HitTestResult]. When the
/// [handleEvent] method is called, the same object that was added to the
/// [HitTestResult] will be passed in, so it can be used to track information
/// like the precise coordinate of the hit, in whatever coordinate system is
/// used by the new layout protocol.
///
/// ### Adapting from one protocol to another
///
/// In general, the root of a Flutter render object tree is a [RenderView]. This
/// object has a single child, which must be a [RenderBox]. Thus, if you want to
/// have a custom [RenderObject] subclass in the render tree, you have two
/// choices: you either need to replace the [RenderView] itself, or you need to
/// have a [RenderBox] that has your class as its child. (The latter is the much
/// more common case.)
///
/// This [RenderBox] subclass converts from the box protocol to the protocol of
/// your class.
///
/// In particular, this means that for hit testing it overrides
/// [RenderBox.hitTest], and calls whatever method you have in your class for
/// hit testing.
///
/// Similarly, it overrides [performLayout] to create a [Constraints] object
/// appropriate for your class and passes that to the child's [layout] method.
///
/// ### Layout interactions between render objects
///
/// In general, the layout of a render object should only depend on the output of
/// its child's layout, and then only if `parentUsesSize` is set to true in the
/// [layout] call. Furthermore, if it is set to true, the parent must call the
/// child's [layout] if the child is to be rendered, because otherwise the
/// parent will not be notified when the child changes its layout outputs.
///
/// It is possible to set up render object protocols that transfer additional
/// information. For example, in the [RenderBox] protocol you can query your
/// children's intrinsic dimensions and baseline geometry. However, if this is
/// done then it is imperative that the child call [markNeedsLayout] on the
/// parent any time that additional information changes, if the parent used it
/// in the last layout phase. For an example of how to implement this, see the
/// [RenderBox.markNeedsLayout] method. It overrides
/// [RenderObject.markNeedsLayout] so that if a parent has queried the intrinsic
/// or baseline information, it gets marked dirty whenever the child's geometry
/// changes.
abstract class RenderObject /* extends AbstractNode with DiagnosticableTreeMixin implements HitTestTarget */ {

    init {
        _needsCompositing = isRepaintBoundary || alwaysNeedsCompositing;
    }

    /// Cause the entire subtree rooted at the given [RenderObject] to be marked
    /// dirty for layout, paint, etc, so that the effects of a hot reload can be
    /// seen, or so that the effect of changing a global debug flag (such as
    /// [debugPaintSizeEnabled]) can be applied.
    ///
    /// This is called by the [RendererBinding] in response to the
    /// `ext.flutter.reassemble` hook, which is used by development tools when the
    /// application code has changed, to cause the widget tree to pick up any
    /// changed implementations.
    ///
    /// This is expensive and should not be called except during development.
    ///
    /// See also:
    ///
    /// * [BindingBase.reassembleApplication].
    void reassemble() {
        markNeedsLayout();
        markNeedsCompositingBitsUpdate();
        markNeedsPaint();
        markNeedsSemanticsUpdate();
        visitChildren((RenderObject child) {
            child.reassemble();
        });
    }

    // LAYOUT

    /// Data for use by the parent render object.
    ///
    /// The parent data is used by the render object that lays out this object
    /// (typically this object's parent in the render tree) to store information
    /// relevant to itself and to any other nodes who happen to know exactly what
    /// the data means. The parent data is opaque to the child.
    ///
    ///  * The parent data field must not be directly set, except by calling
    ///    [setupParentData] on the parent node.
    ///  * The parent data can be set before the child is added to the parent, by
    ///    calling [setupParentData] on the future parent node.
    ///  * The conventions for using the parent data depend on the layout protocol
    ///    used between the parent and child. For example, in box layout, the
    ///    parent data is completely opaque but in sector layout the child is
    ///    permitted to read some fields of the parent data.
    var parentData: ParentData? = null;

    /// Override to setup parent data correctly for your children.
    ///
    /// You can call this function to set up the parent data for child before the
    /// child is added to the parent's child list.
    // TODO(Migration/Filip): Dropped covariant
    fun setupParentData(child: RenderObject) {
        assert(_debugCanPerformMutations);
        if (child.parentData !is ParentData)
        child.parentData = ParentData();
    }

    /// Called by subclasses when they decide a render object is a child.
    ///
    /// Only for use by subclasses when changing their child lists. Calling this
    /// in other cases will lead to an inconsistent tree and probably cause crashes.
    override fun adoptChild(child: RenderObject) {
        assert(_debugCanPerformMutations);
        assert(child != null);
        setupParentData(child);
        super.adoptChild(child);
        markNeedsLayout();
        markNeedsCompositingBitsUpdate();
        markNeedsSemanticsUpdate();
    }

    /// Called by subclasses when they decide a render object is no longer a child.
    ///
    /// Only for use by subclasses when changing their child lists. Calling this
    /// in other cases will lead to an inconsistent tree and probably cause crashes.
    override fun dropChild(child: RenderObject) {
        assert(_debugCanPerformMutations);
        assert(child != null);
        assert(child.parentData != null);
        child._cleanRelayoutBoundary();
        child.parentData.detach();
        child.parentData = null;
        super.dropChild(child);
        markNeedsLayout();
        markNeedsCompositingBitsUpdate();
        markNeedsSemanticsUpdate();
    }

    /// Calls visitor for each immediate child of this render object.
    ///
    /// Override in subclasses with children and call the visitor for each child.
    fun visitChildren(visitor: RenderObjectVisitor) { }
//
//    /// The object responsible for creating this render object.
//    ///
//    /// Used in debug messages.
//    dynamic debugCreator;
//
//    void _debugReportException(String method, dynamic exception, StackTrace stack) {
//        FlutterError.reportError(new FlutterErrorDetailsForRendering(
//                exception: exception,
//                stack: stack,
//                library: 'rendering library',
//        context: 'during $method()',
//        renderObject: this,
//        informationCollector: (StringBuffer information) {
//        information.writeln('The following RenderObject was being processed when the exception was fired:');
//        information.writeln('  ${toStringShallow(joiner: '\n  ')}');
//        final List<String> descendants = <String>[];
//        const int maxDepth = 5;
//        int depth = 0;
//        const int maxLines = 25;
//        int lines = 0;
//        void visitor(RenderObject child) {
//            if (lines < maxLines) {
//                depth += 1;
//                descendants.add('${"  " * depth}$child');
//                if (depth < maxDepth)
//                    child.visitChildren(visitor);
//                depth -= 1;
//            } else if (lines == maxLines) {
//                descendants.add('  ...(descendants list truncated after $lines lines)');
//            }
//            lines += 1;
//        }
//        visitChildren(visitor);
//        if (lines > 1) {
//            information.writeln('This RenderObject had the following descendants (showing up to depth $maxDepth):');
//        } else if (descendants.length == 1) {
//            information.writeln('This RenderObject had the following child:');
//        } else {
//            information.writeln('This RenderObject has no descendants.');
//        }
//        information.writeAll(descendants, '\n');
//    }
//        ));
//    }

    /// Whether [performResize] for this render object is currently running.
    ///
    /// Only valid when asserts are enabled. In release builds, always returns
    /// false.
    var debugDoingThisResize = false
        private set

    /// Whether [performLayout] for this render object is currently running.
    ///
    /// Only valid when asserts are enabled. In release builds, always returns
    /// false.
    var debugDoingThisLayout = false
        private set

    /// The render object that is actively computing layout.
    ///
    /// Only valid when asserts are enabled. In release builds, always returns
    /// null.
    static RenderObject get debugActiveLayout => _debugActiveLayout;
    static RenderObject _debugActiveLayout;

    /// Whether the parent render object is permitted to use this render object's
    /// size.
    ///
    /// Determined by the `parentUsesSize` parameter to [layout].
    ///
    /// Only valid when asserts are enabled. In release builds, always returns
    /// null.
    var debugCanParentUseSize = false
        private set

    private var _debugMutationsLocked = false;

//    /// Whether tree mutations are currently permitted.
//    ///
//    /// Only valid when asserts are enabled. In release builds, always returns
//    /// null.
//    bool get _debugCanPerformMutations {
//        var result: Boolean = false;
//        assert(() {
//            RenderObject node = this;
//            while (true) {
//                if (node._doingThisLayoutWithCallback) {
//                    result = true;
//                    break;
//                }
//                if (owner != null && owner._debugAllowMutationsToDirtySubtrees && node._needsLayout) {
//                    result = true;
//                    break;
//                }
//                if (node._debugMutationsLocked) {
//                    result = false;
//                    break;
//                }
//                if (node.parent is! RenderObject) {
//                    result = true;
//                    break;
//                }
//                node = node.parent;
//            }
//            return true;
//        }());
//        return result;
//    }
//
//    @override
//    PipelineOwner get owner => super.owner;
//
//    @override
//    void attach(PipelineOwner owner) {
//        super.attach(owner);
//        // If the node was dirtied in some way while unattached, make sure to add
//        // it to the appropriate dirty list now that an owner is available
//        if (_needsLayout && _relayoutBoundary != null) {
//            // Don't enter this block if we've never laid out at all;
//            // scheduleInitialLayout() will handle it
//            _needsLayout = false;
//            markNeedsLayout();
//        }
//        if (_needsCompositingBitsUpdate) {
//            _needsCompositingBitsUpdate = false;
//            markNeedsCompositingBitsUpdate();
//        }
//        if (_needsPaint && _layer != null) {
//            // Don't enter this block if we've never painted at all;
//            // scheduleInitialPaint() will handle it
//            _needsPaint = false;
//            markNeedsPaint();
//        }
//        if (_needsSemanticsUpdate && _semanticsConfiguration.isSemanticBoundary) {
//            // Don't enter this block if we've never updated semantics at all;
//            // scheduleInitialSemantics() will handle it
//            _needsSemanticsUpdate = false;
//            markNeedsSemanticsUpdate();
//        }
//    }
//
//    /// Whether this render object's layout information is dirty.
//    ///
//    /// This is only set in debug mode. In general, render objects should not need
//    /// to condition their runtime behavior on whether they are dirty or not,
//    /// since they should only be marked dirty immediately prior to being laid
//    /// out and painted.
//    ///
//    /// It is intended to be used by tests and asserts.
//    bool get debugNeedsLayout {
//        bool result;
//        assert(() {
//            result = _needsLayout;
//            return true;
//        }());
//        return result;
//    }
//    bool _needsLayout = true;
//
//    RenderObject _relayoutBoundary;
//    bool _doingThisLayoutWithCallback = false;
//
//    /// The layout constraints most recently supplied by the parent.
//    @protected
//    Constraints get constraints => _constraints;
//    Constraints _constraints;
//
//    /// Verify that the object's constraints are being met. Override
//    /// this function in a subclass to verify that your state matches
//    /// the constraints object. This function is only called in checked
//    /// mode and only when needsLayout is false. If the constraints are
//    /// not met, it should assert or throw an exception.
//    @protected
//    void debugAssertDoesMeetConstraints();
//
//    /// When true, debugAssertDoesMeetConstraints() is currently
//    /// executing asserts for verifying the consistent behavior of
//    /// intrinsic dimensions methods.
//    ///
//    /// This should only be set by debugAssertDoesMeetConstraints()
//    /// implementations. It is used by tests to selectively ignore
//    /// custom layout callbacks. It should not be set outside of
//    /// debugAssertDoesMeetConstraints(), and should not be checked in
//    /// release mode (where it will always be false).
//    static bool debugCheckingIntrinsics = false;
//    bool _debugSubtreeRelayoutRootAlreadyMarkedNeedsLayout() {
//        if (_relayoutBoundary == null)
//            return true; // we haven't yet done layout even once, so there's nothing for us to do
//        RenderObject node = this;
//        while (node != _relayoutBoundary) {
//            assert(node._relayoutBoundary == _relayoutBoundary);
//            assert(node.parent != null);
//            node = node.parent;
//            if ((!node._needsLayout) && (!node._debugDoingThisLayout))
//                return false;
//        }
//        assert(node._relayoutBoundary == node);
//        return true;
//    }

    /// Mark this render object's layout information as dirty, and either register
    /// this object with its [PipelineOwner], or defer to the parent, depending on
    /// whether this object is a relayout boundary or not respectively.
    ///
    /// ## Background
    ///
    /// Rather than eagerly updating layout information in response to writes into
    /// a render object, we instead mark the layout information as dirty, which
    /// schedules a visual update. As part of the visual update, the rendering
    /// pipeline updates the render object's layout information.
    ///
    /// This mechanism batches the layout work so that multiple sequential writes
    /// are coalesced, removing redundant computation.
    ///
    /// If a render object's parent indicates that it uses the size of one of its
    /// render object children when computing its layout information, this
    /// function, when called for the child, will also mark the parent as needing
    /// layout. In that case, since both the parent and the child need to have
    /// their layout recomputed, the pipeline owner is only notified about the
    /// parent; when the parent is laid out, it will call the child's [layout]
    /// method and thus the child will be laid out as well.
    ///
    /// Once [markNeedsLayout] has been called on a render object,
    /// [debugNeedsLayout] returns true for that render object until just after
    /// the pipeline owner has called [layout] on the render object.
    ///
    /// ## Special cases
    ///
    /// Some subclasses of [RenderObject], notably [RenderBox], have other
    /// situations in which the parent needs to be notified if the child is
    /// dirtied. Such subclasses override markNeedsLayout and either call
    /// `super.markNeedsLayout()`, in the normal case, or call
    /// [markParentNeedsLayout], in the case where the parent needs to be laid out
    /// as well as the child.
    ///
    /// If [sizedByParent] has changed, called
    /// [markNeedsLayoutForSizedByParentChange] instead of [markNeedsLayout].
    fun markNeedsLayout() {
        assert(_debugCanPerformMutations);
        if (_needsLayout) {
            assert(_debugSubtreeRelayoutRootAlreadyMarkedNeedsLayout());
            return;
        }
        assert(_relayoutBoundary != null);
        if (_relayoutBoundary != this) {
            markParentNeedsLayout();
        } else {
            _needsLayout = true;
            if (owner != null) {
                assert(() {
                    if (debugPrintMarkNeedsLayoutStacks)
                        debugPrintStack(label: 'markNeedsLayout() called for $this');
                    return true;
                }());
                owner._nodesNeedingLayout.add(this);
                owner.requestVisualUpdate();
            }
        }
    }

    /// Mark this render object's layout information as dirty, and then defer to
    /// the parent.
    ///
    /// This function should only be called from [markNeedsLayout] or
    /// [markNeedsLayoutForSizedByParentChange] implementations of subclasses that
    /// introduce more reasons for deferring the handling of dirty layout to the
    /// parent. See [markNeedsLayout] for details.
    ///
    /// Only call this if [parent] is not null.
    protected fun markParentNeedsLayout() {
        _needsLayout = true;
        final RenderObject parent = this.parent;
        if (!_doingThisLayoutWithCallback) {
            parent.markNeedsLayout();
        } else {
            assert(parent._debugDoingThisLayout);
        }
        assert(parent == this.parent);
    }

    /// Mark this render object's layout information as dirty (like
    /// [markNeedsLayout]), and additionally also handle any necessary work to
    /// handle the case where [sizedByParent] has changed value.
    ///
    /// This should be called whenever [sizedByParent] might have changed.
    ///
    /// Only call this if [parent] is not null.
    protected fun markNeedsLayoutForSizedByParentChange() {
        markNeedsLayout();
        markParentNeedsLayout();
    }

//    void _cleanRelayoutBoundary() {
//        if (_relayoutBoundary != this) {
//            _relayoutBoundary = null;
//            _needsLayout = true;
//            visitChildren((RenderObject child) {
//                child._cleanRelayoutBoundary();
//            });
//        }
//    }
//
//    /// Bootstrap the rendering pipeline by scheduling the very first layout.
//    ///
//    /// Requires this render object to be attached and that this render object
//    /// is the root of the render tree.
//    ///
//    /// See [RenderView] for an example of how this function is used.
//    void scheduleInitialLayout() {
//        assert(attached);
//        assert(parent is! RenderObject);
//        assert(!owner._debugDoingLayout);
//        assert(_relayoutBoundary == null);
//        _relayoutBoundary = this;
//        assert(() {
//            _debugCanParentUseSize = false;
//            return true;
//        }());
//        owner._nodesNeedingLayout.add(this);
//    }
//
//    void _layoutWithoutResize() {
//        assert(_relayoutBoundary == this);
//        RenderObject debugPreviousActiveLayout;
//        assert(!_debugMutationsLocked);
//        assert(!_doingThisLayoutWithCallback);
//        assert(_debugCanParentUseSize != null);
//        assert(() {
//            _debugMutationsLocked = true;
//            _debugDoingThisLayout = true;
//            debugPreviousActiveLayout = _debugActiveLayout;
//            _debugActiveLayout = this;
//            if (debugPrintLayouts)
//                debugPrint('Laying out (without resize) $this');
//            return true;
//        }());
//        try {
//            performLayout();
//            markNeedsSemanticsUpdate();
//        } catch (e, stack) {
//        _debugReportException('performLayout', e, stack);
//    }
//        assert(() {
//            _debugActiveLayout = debugPreviousActiveLayout;
//            _debugDoingThisLayout = false;
//            _debugMutationsLocked = false;
//            return true;
//        }());
//        _needsLayout = false;
//        markNeedsPaint();
//    }
//
//    /// Compute the layout for this render object.
//    ///
//    /// This method is the main entry point for parents to ask their children to
//    /// update their layout information. The parent passes a constraints object,
//    /// which informs the child as which layouts are permissible. The child is
//    /// required to obey the given constraints.
//    ///
//    /// If the parent reads information computed during the child's layout, the
//    /// parent must pass true for parentUsesSize. In that case, the parent will be
//    /// marked as needing layout whenever the child is marked as needing layout
//    /// because the parent's layout information depends on the child's layout
//    /// information. If the parent uses the default value (false) for
//    /// parentUsesSize, the child can change its layout information (subject to
//    /// the given constraints) without informing the parent.
//    ///
//    /// Subclasses should not override [layout] directly. Instead, they should
//    /// override [performResize] and/or [performLayout]. The [layout] method
//    /// delegates the actual work to [performResize] and [performLayout].
//    ///
//    /// The parent's performLayout method should call the [layout] of all its
//    /// children unconditionally. It is the [layout] method's responsibility (as
//    /// implemented here) to return early if the child does not need to do any
//    /// work to update its layout information.
//    void layout(Constraints constraints, { bool parentUsesSize: false }) {
//        assert(constraints != null);
//        assert(constraints.debugAssertIsValid(
//                isAppliedConstraint: true,
//        informationCollector: (StringBuffer information) {
//        final List<String> stack = StackTrace.current.toString().split('\n');
//        int targetFrame;
//        final Pattern layoutFramePattern = new RegExp(r'^#[0-9]+ +RenderObject.layout \(');
//        for (int i = 0; i < stack.length; i += 1) {
//        if (layoutFramePattern.matchAsPrefix(stack[i]) != null) {
//            targetFrame = i + 1;
//            break;
//        }
//    }
//        if (targetFrame != null && targetFrame < stack.length) {
//            information.writeln(
//                    'These invalid constraints were provided to $runtimeType\'s layout() '
//                    'function by the following function, which probably computed the '
//            'invalid constraints in question:'
//            );
//            final Pattern targetFramePattern = new RegExp(r'^#[0-9]+ +(.+)$');
//            final Match targetFrameMatch = targetFramePattern.matchAsPrefix(stack[targetFrame]);
//            if (targetFrameMatch != null && targetFrameMatch.groupCount > 0) {
//                information.writeln('  ${targetFrameMatch.group(1)}');
//            } else {
//                information.writeln(stack[targetFrame]);
//            }
//        }
//    }
//        ));
//        assert(!_debugDoingThisResize);
//        assert(!_debugDoingThisLayout);
//        RenderObject relayoutBoundary;
//        if (!parentUsesSize || sizedByParent || constraints.isTight || parent is! RenderObject) {
//        relayoutBoundary = this;
//    } else {
//        final RenderObject parent = this.parent;
//        relayoutBoundary = parent._relayoutBoundary;
//    }
//        assert(() {
//            _debugCanParentUseSize = parentUsesSize;
//            return true;
//        }());
//        if (!_needsLayout && constraints == _constraints && relayoutBoundary == _relayoutBoundary) {
//            assert(() {
//                // in case parentUsesSize changed since the last invocation, set size
//                // to itself, so it has the right internal debug values.
//                _debugDoingThisResize = sizedByParent;
//                _debugDoingThisLayout = !sizedByParent;
//                final RenderObject debugPreviousActiveLayout = _debugActiveLayout;
//                _debugActiveLayout = this;
//                debugResetSize();
//                _debugActiveLayout = debugPreviousActiveLayout;
//                _debugDoingThisLayout = false;
//                _debugDoingThisResize = false;
//                return true;
//            }());
//            return;
//        }
//        _constraints = constraints;
//        _relayoutBoundary = relayoutBoundary;
//        assert(!_debugMutationsLocked);
//        assert(!_doingThisLayoutWithCallback);
//        assert(() {
//            _debugMutationsLocked = true;
//            if (debugPrintLayouts)
//                debugPrint('Laying out (${sizedByParent ? "with separate resize" : "with resize allowed"}) $this');
//            return true;
//        }());
//        if (sizedByParent) {
//            assert(() { _debugDoingThisResize = true; return true; }());
//            try {
//                performResize();
//                assert(() { debugAssertDoesMeetConstraints(); return true; }());
//            } catch (e, stack) {
//                _debugReportException('performResize', e, stack);
//            }
//            assert(() { _debugDoingThisResize = false; return true; }());
//        }
//        RenderObject debugPreviousActiveLayout;
//        assert(() {
//            _debugDoingThisLayout = true;
//            debugPreviousActiveLayout = _debugActiveLayout;
//            _debugActiveLayout = this;
//            return true;
//        }());
//        try {
//            performLayout();
//            markNeedsSemanticsUpdate();
//            assert(() { debugAssertDoesMeetConstraints(); return true; }());
//        } catch (e, stack) {
//        _debugReportException('performLayout', e, stack);
//    }
//        assert(() {
//            _debugActiveLayout = debugPreviousActiveLayout;
//            _debugDoingThisLayout = false;
//            _debugMutationsLocked = false;
//            return true;
//        }());
//        _needsLayout = false;
//        markNeedsPaint();
//    }
//
//    /// If a subclass has a "size" (the state controlled by `parentUsesSize`,
//    /// whatever it is in the subclass, e.g. the actual `size` property of
//    /// [RenderBox]), and the subclass verifies that in checked mode this "size"
//    /// property isn't used when [debugCanParentUseSize] isn't set, then that
//    /// subclass should override [debugResetSize] to reapply the current values of
//    /// [debugCanParentUseSize] to that state.
//    @protected
//    void debugResetSize() { }
//
//    /// Whether the constraints are the only input to the sizing algorithm (in
//    /// particular, child nodes have no impact).
//    ///
//    /// Returning false is always correct, but returning true can be more
//    /// efficient when computing the size of this render object because we don't
//    /// need to recompute the size if the constraints don't change.
//    ///
//    /// Typically, subclasses will always return the same value. If the value can
//    /// change, then, when it does change, the subclass should make sure to call
//    /// [markNeedsLayoutForSizedByParentChange].
//    @protected
//    bool get sizedByParent => false;
//
//    /// Updates the render objects size using only the constraints.
//    ///
//    /// Do not call this function directly: call [layout] instead. This function
//    /// is called by [layout] when there is actually work to be done by this
//    /// render object during layout. The layout constraints provided by your
//    /// parent are available via the [constraints] getter.
//    ///
//    /// Subclasses that set [sizedByParent] to true should override this method
//    /// to compute their size.
//    ///
//    /// This function is called only if [sizedByParent] is true.
//    @protected
//    void performResize();
//
//    /// Do the work of computing the layout for this render object.
//    ///
//    /// Do not call this function directly: call [layout] instead. This function
//    /// is called by [layout] when there is actually work to be done by this
//    /// render object during layout. The layout constraints provided by your
//    /// parent are available via the [constraints] getter.
//    ///
//    /// If [sizedByParent] is true, then this function should not actually change
//    /// the dimensions of this render object. Instead, that work should be done by
//    /// [performResize]. If [sizedByParent] is false, then this function should
//    /// both change the dimensions of this render object and instruct its children
//    /// to layout.
//    ///
//    /// In implementing this function, you must call [layout] on each of your
//    /// children, passing true for parentUsesSize if your layout information is
//    /// dependent on your child's layout information. Passing true for
//    /// parentUsesSize ensures that this render object will undergo layout if the
//    /// child undergoes layout. Otherwise, the child can changes its layout
//    /// information without informing this render object.
//    @protected
//    void performLayout();
//
//    /// Allows mutations to be made to this object's child list (and any
//    /// descendants) as well as to any other dirty nodes in the render tree owned
//    /// by the same [PipelineOwner] as this object. The `callback` argument is
//    /// invoked synchronously, and the mutations are allowed only during that
//    /// callback's execution.
//    ///
//    /// This exists to allow child lists to be built on-demand during layout (e.g.
//    /// based on the object's size), and to enable nodes to be moved around the
//    /// tree as this happens (e.g. to handle [GlobalKey] reparenting), while still
//    /// ensuring that any particular node is only laid out once per frame.
//    ///
//    /// Calling this function disables a number of assertions that are intended to
//    /// catch likely bugs. As such, using this function is generally discouraged.
//    ///
//    /// This function can only be called during layout.
//    @protected
//    void invokeLayoutCallback<T extends Constraints>(LayoutCallback<T> callback) {
//        assert(_debugMutationsLocked);
//        assert(_debugDoingThisLayout);
//        assert(!_doingThisLayoutWithCallback);
//        _doingThisLayoutWithCallback = true;
//        try {
//            owner._enableMutationsToDirtySubtrees(() { callback(constraints); });
//        } finally {
//            _doingThisLayoutWithCallback = false;
//        }
//    }
//
//    /// Rotate this render object (not yet implemented).
//    void rotate({
//        int oldAngle, // 0..3
//        int newAngle, // 0..3
//        Duration time
//    }) { }
//
//    // when the parent has rotated (e.g. when the screen has been turned
//    // 90 degrees), immediately prior to layout() being called for the
//    // new dimensions, rotate() is called with the old and new angles.
//    // The next time paint() is called, the coordinate space will have
//    // been rotated N quarter-turns clockwise, where:
//    //    N = newAngle-oldAngle
//    // ...but the rendering is expected to remain the same, pixel for
//    // pixel, on the output device. Then, the layout() method or
//    // equivalent will be called.
//
//
//    // PAINTING
//
//    /// Whether [paint] for this render object is currently running.
//    ///
//    /// Only valid when asserts are enabled. In release builds, always returns
//    /// false.
//    bool get debugDoingThisPaint => _debugDoingThisPaint;
//    bool _debugDoingThisPaint = false;
//
//    /// The render object that is actively painting.
//    ///
//    /// Only valid when asserts are enabled. In release builds, always returns
//    /// null.
//    static RenderObject get debugActivePaint => _debugActivePaint;
//    static RenderObject _debugActivePaint;
//
//    /// Whether this render object repaints separately from its parent.
//    ///
//    /// Override this in subclasses to indicate that instances of your class ought
//    /// to repaint independently. For example, render objects that repaint
//    /// frequently might want to repaint themselves without requiring their parent
//    /// to repaint.
//    ///
//    /// If this getter returns true, the [paintBounds] are applied to this object
//    /// and all descendants.
//    ///
//    /// Warning: This getter must not change value over the lifetime of this object.
//    bool get isRepaintBoundary => false;
//
//    /// Called, in checked mode, if [isRepaintBoundary] is true, when either the
//    /// this render object or its parent attempt to paint.
//    ///
//    /// This can be used to record metrics about whether the node should actually
//    /// be a repaint boundary.
//    void debugRegisterRepaintBoundaryPaint({ bool includedParent: true, bool includedChild: false }) { }
//
//    /// Whether this render object always needs compositing.
//    ///
//    /// Override this in subclasses to indicate that your paint function always
//    /// creates at least one composited layer. For example, videos should return
//    /// true if they use hardware decoders.
//    ///
//    /// You must call [markNeedsCompositingBitsUpdate] if the value of this getter
//    /// changes. (This is implied when [adoptChild] or [dropChild] are called.)
//    @protected
//    bool get alwaysNeedsCompositing => false;
//
//    OffsetLayer _layer;
//    /// The compositing layer that this render object uses to repaint.
//    ///
//    /// Call only when [isRepaintBoundary] is true and the render object has
//    /// already painted.
//    ///
//    /// To access the layer in debug code, even when it might be inappropriate to
//    /// access it (e.g. because it is dirty), consider [debugLayer].
//    OffsetLayer get layer {
//        assert(isRepaintBoundary, 'You can only access RenderObject.layer for render objects that are repaint boundaries.');
//        assert(!_needsPaint);
//        return _layer;
//    }
//    /// In debug mode, the compositing layer that this render object uses to repaint.
//    ///
//    /// This getter is intended for debugging purposes only. In release builds, it
//    /// always returns null. In debug builds, it returns the layer even if the layer
//    /// is dirty.
//    ///
//    /// For production code, consider [layer].
//    OffsetLayer get debugLayer {
//        OffsetLayer result;
//        assert(() {
//            result = _layer;
//            return true;
//        }());
//        return result;
//    }

    var _needsCompositingBitsUpdate = false; // set to true when a child is added
    /// Mark the compositing state for this render object as dirty.
    ///
    /// When the subtree is mutated, we need to recompute our
    /// [needsCompositing] bit, and some of our ancestors need to do the
    /// same (in case ours changed in a way that will change theirs). To
    /// this end, [adoptChild] and [dropChild] call this method, and, as
    /// necessary, this method calls the parent's, etc, walking up the
    /// tree to mark all the nodes that need updating.
    ///
    /// This method does not schedule a rendering frame, because since
    /// it cannot be the case that _only_ the compositing bits changed,
    /// something else will have scheduled a frame for us.
    fun markNeedsCompositingBitsUpdate() {
        if (_needsCompositingBitsUpdate)
            return;
        _needsCompositingBitsUpdate = true;
        if (parent is RenderObject) {
            final RenderObject parent = this.parent;
            if (parent._needsCompositingBitsUpdate)
                return;
            if (!isRepaintBoundary && !parent.isRepaintBoundary) {
                parent.markNeedsCompositingBitsUpdate();
                return;
            }
        }
        assert(() {
            final AbstractNode parent = this.parent;
            if (parent is RenderObject)
                return parent._needsCompositing;
            return true;
        }());
        // parent is fine (or there isn't one), but we are dirty
        if (owner != null)
            owner._nodesNeedingCompositingBitsUpdate.add(this);
    }

//    bool _needsCompositing; // initialised in the constructor
//    /// Whether we or one of our descendants has a compositing layer.
//    ///
//    /// Only legal to call after [PipelineOwner.flushLayout] and
//    /// [PipelineOwner.flushCompositingBits] have been called.
//    bool get needsCompositing {
//        assert(!_needsCompositingBitsUpdate); // make sure we don't use this bit when it is dirty
//        return _needsCompositing;
//    }
//
//    void _updateCompositingBits() {
//        if (!_needsCompositingBitsUpdate)
//            return;
//        final bool oldNeedsCompositing = _needsCompositing;
//        _needsCompositing = false;
//        visitChildren((RenderObject child) {
//            child._updateCompositingBits();
//            if (child.needsCompositing)
//                _needsCompositing = true;
//        });
//        if (isRepaintBoundary || alwaysNeedsCompositing)
//            _needsCompositing = true;
//        if (oldNeedsCompositing != _needsCompositing)
//            markNeedsPaint();
//        _needsCompositingBitsUpdate = false;
//    }
//
//    /// Whether this render object's paint information is dirty.
//    ///
//    /// This is only set in debug mode. In general, render objects should not need
//    /// to condition their runtime behavior on whether they are dirty or not,
//    /// since they should only be marked dirty immediately prior to being laid
//    /// out and painted.
//    ///
//    /// It is intended to be used by tests and asserts.
//    ///
//    /// It is possible (and indeed, quite common) for [debugNeedsPaint] to be
//    /// false and [debugNeedsLayout] to be true. The render object will still be
//    /// repainted in the next frame when this is the case, because the
//    /// [markNeedsPaint] method is implicitly called by the framework after a
//    /// render object is laid out, prior to the paint phase.
//    bool get debugNeedsPaint {
//        bool result;
//        assert(() {
//            result = _needsPaint;
//            return true;
//        }());
//        return result;
//    }
//    bool _needsPaint = true;
//
//    /// Mark this render object as having changed its visual appearance.
//    ///
//    /// Rather than eagerly updating this render object's display list
//    /// in response to writes, we instead mark the render object as needing to
//    /// paint, which schedules a visual update. As part of the visual update, the
//    /// rendering pipeline will give this render object an opportunity to update
//    /// its display list.
//    ///
//    /// This mechanism batches the painting work so that multiple sequential
//    /// writes are coalesced, removing redundant computation.
//    ///
//    /// Once [markNeedsPaint] has been called on a render object,
//    /// [debugNeedsPaint] returns true for that render object until just after
//    /// the pipeline owner has called [paint] on the render object.
//    void markNeedsPaint() {
//        assert(owner == null || !owner.debugDoingPaint);
//        if (_needsPaint)
//            return;
//        _needsPaint = true;
//        if (isRepaintBoundary) {
//            assert(() {
//                if (debugPrintMarkNeedsPaintStacks)
//                    debugPrintStack(label: 'markNeedsPaint() called for $this');
//                return true;
//            }());
//            // If we always have our own layer, then we can just repaint
//            // ourselves without involving any other nodes.
//            assert(_layer != null);
//            if (owner != null) {
//                owner._nodesNeedingPaint.add(this);
//                owner.requestVisualUpdate();
//            }
//        } else if (parent is RenderObject) {
//            // We don't have our own layer; one of our ancestors will take
//            // care of updating the layer we're in and when they do that
//            // we'll get our paint() method called.
//            assert(_layer == null);
//            final RenderObject parent = this.parent;
//            parent.markNeedsPaint();
//            assert(parent == this.parent);
//        } else {
//            assert(() {
//                if (debugPrintMarkNeedsPaintStacks)
//                    debugPrintStack(label: 'markNeedsPaint() called for $this (root of render tree)');
//                return true;
//            }());
//            // If we're the root of the render tree (probably a RenderView),
//            // then we have to paint ourselves, since nobody else can paint
//            // us. We don't add ourselves to _nodesNeedingPaint in this
//            // case, because the root is always told to paint regardless.
//            if (owner != null)
//                owner.requestVisualUpdate();
//        }
//    }
//
//    // Called when flushPaint() tries to make us paint but our layer is detached.
//    // To make sure that our subtree is repainted when it's finally reattached,
//    // even in the case where some ancestor layer is itself never marked dirty, we
//    // have to mark our entire detached subtree as dirty and needing to be
//    // repainted. That way, we'll eventually be repainted.
//    void _skippedPaintingOnLayer() {
//        assert(attached);
//        assert(isRepaintBoundary);
//        assert(_needsPaint);
//        assert(_layer != null);
//        assert(!_layer.attached);
//        AbstractNode ancestor = parent;
//        while (ancestor is RenderObject) {
//            final RenderObject node = ancestor;
//            if (node.isRepaintBoundary) {
//                if (node._layer == null)
//                    break; // looks like the subtree here has never been painted. let it handle itself.
//                if (node._layer.attached)
//                    break; // it's the one that detached us, so it's the one that will decide to repaint us.
//                node._needsPaint = true;
//            }
//            ancestor = node.parent;
//        }
//    }
//
//    /// Bootstrap the rendering pipeline by scheduling the very first paint.
//    ///
//    /// Requires that this render object is attached, is the root of the render
//    /// tree, and has a composited layer.
//    ///
//    /// See [RenderView] for an example of how this function is used.
//    void scheduleInitialPaint(ContainerLayer rootLayer) {
//        assert(rootLayer.attached);
//        assert(attached);
//        assert(parent is! RenderObject);
//        assert(!owner._debugDoingPaint);
//        assert(isRepaintBoundary);
//        assert(_layer == null);
//        _layer = rootLayer;
//        assert(_needsPaint);
//        owner._nodesNeedingPaint.add(this);
//    }
//
//    /// Replace the layer. This is only valid for the root of a render
//    /// object subtree (whatever object [scheduleInitialPaint] was
//    /// called on).
//    ///
//    /// This might be called if, e.g., the device pixel ratio changed.
//    void replaceRootLayer(OffsetLayer rootLayer) {
//        assert(rootLayer.attached);
//        assert(attached);
//        assert(parent is! RenderObject);
//        assert(!owner._debugDoingPaint);
//        assert(isRepaintBoundary);
//        assert(_layer != null); // use scheduleInitialPaint the first time
//        _layer.detach();
//        _layer = rootLayer;
//        markNeedsPaint();
//    }
//
//    void _paintWithContext(PaintingContext context, Offset offset) {
//        assert(() {
//            if (_debugDoingThisPaint) {
//                throw new FlutterError(
//                        'Tried to paint a RenderObject reentrantly.\n'
//                'The following RenderObject was already being painted when it was '
//                'painted again:\n'
//                '  ${toStringShallow(joiner: "\n    ")}\n'
//                'Since this typically indicates an infinite recursion, it is '
//                'disallowed.'
//                );
//            }
//            return true;
//        }());
//        // If we still need layout, then that means that we were skipped in the
//        // layout phase and therefore don't need painting. We might not know that
//        // yet (that is, our layer might not have been detached yet), because the
//        // same node that skipped us in layout is above us in the tree (obviously)
//        // and therefore may not have had a chance to paint yet (since the tree
//        // paints in reverse order). In particular this will happen if they have
//        // a different layer, because there's a repaint boundary between us.
//        if (_needsLayout)
//            return;
//        assert(() {
//            if (_needsCompositingBitsUpdate) {
//                throw new FlutterError(
//                        'Tried to paint a RenderObject before its compositing bits were '
//                'updated.\n'
//                'The following RenderObject was marked as having dirty compositing '
//                'bits at the time that it was painted:\n'
//                '  ${toStringShallow(joiner: "\n    ")}\n'
//                'A RenderObject that still has dirty compositing bits cannot be '
//                'painted because this indicates that the tree has not yet been '
//                'properly configured for creating the layer tree.\n'
//                'This usually indicates an error in the Flutter framework itself.'
//                );
//            }
//            return true;
//        }());
//        RenderObject debugLastActivePaint;
//        assert(() {
//            _debugDoingThisPaint = true;
//            debugLastActivePaint = _debugActivePaint;
//            _debugActivePaint = this;
//            assert(!isRepaintBoundary || _layer != null);
//            return true;
//        }());
//        _needsPaint = false;
//        try {
//            paint(context, offset);
//            assert(!_needsLayout); // check that the paint() method didn't mark us dirty again
//            assert(!_needsPaint); // check that the paint() method didn't mark us dirty again
//        } catch (e, stack) {
//        _debugReportException('paint', e, stack);
//    }
//        assert(() {
//            debugPaint(context, offset);
//            _debugActivePaint = debugLastActivePaint;
//            _debugDoingThisPaint = false;
//            return true;
//        }());
//    }
//
//    /// An estimate of the bounds within which this render object will paint.
//    /// Useful for debugging flags such as [debugPaintLayerBordersEnabled].
//    Rect get paintBounds;
//
//    /// Override this method to paint debugging information.
//    @protected
//    void debugPaint(PaintingContext context, Offset offset) { }
//
//    /// Paint this render object into the given context at the given offset.
//    ///
//    /// Subclasses should override this method to provide a visual appearance
//    /// for themselves. The render object's local coordinate system is
//    /// axis-aligned with the coordinate system of the context's canvas and the
//    /// render object's local origin (i.e, x=0 and y=0) is placed at the given
//    /// offset in the context's canvas.
//    ///
//    /// Do not call this function directly. If you wish to paint yourself, call
//    /// [markNeedsPaint] instead to schedule a call to this function. If you wish
//    /// to paint one of your children, call [PaintingContext.paintChild] on the
//    /// given `context`.
//    ///
//    /// When painting one of your children (via a paint child function on the
//    /// given context), the current canvas held by the context might change
//    /// because draw operations before and after painting children might need to
//    /// be recorded on separate compositing layers.
//    void paint(PaintingContext context, Offset offset) { }
//
//    /// Applies the transform that would be applied when painting the given child
//    /// to the given matrix.
//    ///
//    /// Used by coordinate conversion functions to translate coordinates local to
//    /// one render object into coordinates local to another render object.
//    void applyPaintTransform(covariant RenderObject child, Matrix4 transform) {
//        assert(child.parent == this);
//    }
//
//    /// Applies the paint transform up the tree to `ancestor`.
//    ///
//    /// Returns a matrix that maps the local paint coordinate system to the
//    /// coordinate system of `ancestor`.
//    ///
//    /// If `ancestor` is null, this method returns a matrix that maps from the
//    /// local paint coordinate system to the coordinate system of the
//    /// [PipelineOwner.rootNode]. For the render tree owner by the
//    /// [RendererBinding] (i.e. for the main render tree displayed on the device)
//    /// this means that this method maps to the global coordinate system in
//    /// logical pixels. To get physical pixels, use [applyPaintTransform] from the
//    /// [RenderView] to further transform the coordinate.
//    Matrix4 getTransformTo(RenderObject ancestor) {
//        assert(attached);
//        if (ancestor == null) {
//            final AbstractNode rootNode = owner.rootNode;
//            if (rootNode is RenderObject)
//                ancestor = rootNode;
//        }
//        final List<RenderObject> renderers = <RenderObject>[];
//        for (RenderObject renderer = this; renderer != ancestor; renderer = renderer.parent) {
//        assert(renderer != null); // Failed to find ancestor in parent chain.
//        renderers.add(renderer);
//    }
//        final Matrix4 transform = new Matrix4.identity();
//        for (int index = renderers.length - 1; index > 0; index -= 1)
//        renderers[index].applyPaintTransform(renderers[index - 1], transform);
//        return transform;
//    }
//
//
//    /// Returns a rect in this object's coordinate system that describes
//    /// the approximate bounding box of the clip rect that would be
//    /// applied to the given child during the paint phase, if any.
//    ///
//    /// Returns null if the child would not be clipped.
//    ///
//    /// This is used in the semantics phase to avoid including children
//    /// that are not physically visible.
//    Rect describeApproximatePaintClip(covariant RenderObject child) => null;
//
//    /// Returns a rect in this object's coordinate system that describes
//    /// which [SemanticsNode]s produced by the `child` should be included in the
//    /// semantics tree. [SemanticsNode]s from the `child` that are positioned
//    /// outside of this rect will be dropped. Child [SemanticsNode]s that are
//    /// positioned inside this rect, but outside of [describeApproximatePaintClip]
//    /// will be included in the tree marked as hidden. Child [SemanticsNode]s
//    /// that are inside of both rect will be included in the tree as regular
//    /// nodes.
//    ///
//    /// This method only returns a non-null value if the semantics clip rect
//    /// is different from the rect returned by [describeApproximatePaintClip].
//    /// If the semantics clip rect and the paint clip rect are the same, this
//    /// method returns null.
//    ///
//    /// A viewport would typically implement this method to include semantic nodes
//    /// in the semantics tree that are currently hidden just before the leading
//    /// or just after the trailing edge. These nodes have to be included in the
//    /// semantics tree to implement implicit accessibility scrolling on iOS where
//    /// the viewport scrolls implicitly when moving the accessibility focus from
//    /// a the last visible node in the viewport to the first hidden one.
//    Rect describeSemanticsClip(covariant RenderObject child) => null;
//
//    // SEMANTICS
//
//    /// Bootstrap the semantics reporting mechanism by marking this node
//    /// as needing a semantics update.
//    ///
//    /// Requires that this render object is attached, and is the root of
//    /// the render tree.
//    ///
//    /// See [RendererBinding] for an example of how this function is used.
//    void scheduleInitialSemantics() {
//        assert(attached);
//        assert(parent is! RenderObject);
//        assert(!owner._debugDoingSemantics);
//        assert(_semantics == null);
//        assert(_needsSemanticsUpdate);
//        assert(owner._semanticsOwner != null);
//        owner._nodesNeedingSemantics.add(this);
//        owner.requestVisualUpdate();
//    }
//
//    /// Report the semantics of this node, for example for accessibility purposes.
//    ///
//    /// This method should be overridden by subclasses that have interesting
//    /// semantic information.
//    ///
//    /// The given [SemanticsConfiguration] object is mutable and should be
//    /// annotated in a manner that describes the current state. No reference
//    /// should be kept to that object; mutating it outside of the context of the
//    /// [describeSemanticsConfiguration] call (for example as a result of
//    /// asynchronous computation) will at best have no useful effect and at worse
//    /// will cause crashes as the data will be in an inconsistent state.
//    ///
//    /// ## Sample code
//    ///
//    /// The following snippet will describe the node as a button that responds to
//    /// tap actions.
//    ///
//    /// ```dart
//    /// abstract class SemanticButtonRenderObject extends RenderObject {
//    ///   @override
//    ///   void describeSemanticsConfiguration(SemanticsConfiguration config) {
//    ///     super.describeSemanticsConfiguration(config);
//    ///     config
//    ///       ..onTap = _handleTap
//    ///       ..label = 'I am a button'
//    ///       ..isButton = true;
//    ///   }
//    ///
//    ///   void _handleTap() {
//    ///     // Do something.
//    ///   }
//    /// }
//    /// ```
//    @protected
//    void describeSemanticsConfiguration(SemanticsConfiguration config) {
//        // Nothing to do by default.
//    }
//
//    /// Sends a [SemanticsEvent] associated with this render object's [SemanticsNode].
//    ///
//    /// If this render object has no semantics information, the first parent
//    /// render object with a non-null semantic node is used.
//    ///
//    /// If semantics are disabled, no events are dispatched.
//    ///
//    /// See [SemanticsNode.sendEvent] for a full description of the behavior.
//    void sendSemanticsEvent(SemanticsEvent semanticsEvent) {
//        if (owner.semanticsOwner == null)
//            return;
//        if (_semantics != null) {
//            _semantics.sendEvent(semanticsEvent);
//        } else if (parent != null) {
//            final RenderObject renderParent = parent;
//            renderParent.sendSemanticsEvent(semanticsEvent);
//        }
//    }
//
//    // Use [_semanticsConfiguration] to access.
//    SemanticsConfiguration _cachedSemanticsConfiguration;
//
//    SemanticsConfiguration get _semanticsConfiguration {
//        if (_cachedSemanticsConfiguration == null) {
//            _cachedSemanticsConfiguration = new SemanticsConfiguration();
//            describeSemanticsConfiguration(_cachedSemanticsConfiguration);
//        }
//        return _cachedSemanticsConfiguration;
//    }
//
//    /// The bounding box, in the local coordinate system, of this
//    /// object, for accessibility purposes.
//    Rect get semanticBounds;
//
//    bool _needsSemanticsUpdate = true;
//    SemanticsNode _semantics;
//
//    /// The semantics of this render object.
//    ///
//    /// Exposed only for testing and debugging. To learn about the semantics of
//    /// render objects in production, obtain a [SemanticsHandle] from
//    /// [PipelineOwner.ensureSemantics].
//    ///
//    /// Only valid when asserts are enabled. In release builds, always returns
//    /// null.
//    SemanticsNode get debugSemantics {
//        SemanticsNode result;
//        assert(() {
//            result = _semantics;
//            return true;
//        }());
//        return result;
//    }
//
//    /// Removes all semantics from this render object and its descendants.
//    ///
//    /// Should only be called on objects whose [parent] is not a [RenderObject].
//    ///
//    /// Override this method if you instantiate new [SemanticsNode]s in an
//    /// overridden [assembleSemanticsNode] method, to dispose of those nodes.
//    @mustCallSuper
//    void clearSemantics() {
//        _needsSemanticsUpdate = true;
//        _semantics = null;
//        visitChildren((RenderObject child) {
//            child.clearSemantics();
//        });
//    }
//
//    /// Mark this node as needing an update to its semantics description.
//    ///
//    /// This must be called whenever the semantics configuration of this
//    /// [RenderObject] as annotated by [describeSemanticsConfiguration] changes in
//    /// any way to update the semantics tree.
//    void markNeedsSemanticsUpdate() {
//        assert(!attached || !owner._debugDoingSemantics);
//        if (!attached || owner._semanticsOwner == null) {
//            _cachedSemanticsConfiguration = null;
//            return;
//        }
//
//        // Dirty the semantics tree starting at `this` until we have reached a
//        // RenderObject that is a semantics boundary. All semantics past this
//        // RenderObject are still up-to date. Therefore, we will later only rebuild
//        // the semantics subtree starting at the identified semantics boundary.
//
//        final bool wasSemanticsBoundary = _semantics != null && _cachedSemanticsConfiguration?.isSemanticBoundary == true;
//        _cachedSemanticsConfiguration = null;
//        bool isEffectiveSemanticsBoundary = _semanticsConfiguration.isSemanticBoundary && wasSemanticsBoundary;
//        RenderObject node = this;
//
//        while (!isEffectiveSemanticsBoundary && node.parent is RenderObject) {
//            if (node != this && node._needsSemanticsUpdate)
//                break;
//            node._needsSemanticsUpdate = true;
//
//            node = node.parent;
//            isEffectiveSemanticsBoundary = node._semanticsConfiguration.isSemanticBoundary;
//            if (isEffectiveSemanticsBoundary && node._semantics == null) {
//                // We have reached a semantics boundary that doesn't own a semantics node.
//                // That means the semantics of this branch are currently blocked and will
//                // not appear in the semantics tree. We can abort the walk here.
//                return;
//            }
//        }
//        if (node != this && _semantics != null && _needsSemanticsUpdate) {
//            // If `this` node has already been added to [owner._nodesNeedingSemantics]
//            // remove it as it is no longer guaranteed that its semantics
//            // node will continue to be in the tree. If it still is in the tree, the
//            // ancestor `node` added to [owner._nodesNeedingSemantics] at the end of
//            // this block will ensure that the semantics of `this` node actually gets
//            // updated.
//            // (See semantics_10_test.dart for an example why this is required).
//            owner._nodesNeedingSemantics.remove(this);
//        }
//        if (!node._needsSemanticsUpdate) {
//            node._needsSemanticsUpdate = true;
//            if (owner != null) {
//                assert(node._semanticsConfiguration.isSemanticBoundary || node.parent is! RenderObject);
//                owner._nodesNeedingSemantics.add(node);
//                owner.requestVisualUpdate();
//            }
//        }
//    }
//
//    /// Updates the semantic information of the render object.
//    void _updateSemantics() {
//        assert(_semanticsConfiguration.isSemanticBoundary || parent is! RenderObject);
//        final _SemanticsFragment fragment = _getSemanticsForParent(
//                mergeIntoParent: _semantics?.parent?.isPartOfNodeMerging ?? false,
//        );
//        assert(fragment is _InterestingSemanticsFragment);
//        final _InterestingSemanticsFragment interestingFragment = fragment;
//        final SemanticsNode node = interestingFragment.compileChildren(
//                parentSemanticsClipRect: _semantics?.parentSemanticsClipRect,
//        parentPaintClipRect: _semantics?.parentPaintClipRect,
//        ).single;
//        // Fragment only wants to add this node's SemanticsNode to the parent.
//        assert(interestingFragment.config == null && node == _semantics);
//    }
//
//    /// Returns the semantics that this node would like to add to its parent.
//    _SemanticsFragment _getSemanticsForParent({
//        @required bool mergeIntoParent,
//    }) {
//        assert(mergeIntoParent != null);
//
//        final SemanticsConfiguration config = _semanticsConfiguration;
//        bool dropSemanticsOfPreviousSiblings = config.isBlockingSemanticsOfPreviouslyPaintedNodes;
//
//        final bool producesForkingFragment = !config.hasBeenAnnotated && !config.isSemanticBoundary;
//        final List<_InterestingSemanticsFragment> fragments = <_InterestingSemanticsFragment>[];
//        final Set<_InterestingSemanticsFragment> toBeMarkedExplicit = new Set<_InterestingSemanticsFragment>();
//        final bool childrenMergeIntoParent = mergeIntoParent || config.isMergingSemanticsOfDescendants;
//
//        visitChildrenForSemantics((RenderObject renderChild) {
//            final _SemanticsFragment fragment = renderChild._getSemanticsForParent(
//                    mergeIntoParent: childrenMergeIntoParent,
//                    );
//            if (fragment.dropsSemanticsOfPreviousSiblings) {
//                fragments.clear();
//                toBeMarkedExplicit.clear();
//                if (!config.isSemanticBoundary)
//                    dropSemanticsOfPreviousSiblings = true;
//            }
//            // Figure out which child fragments are to be made explicit.
//            for (_InterestingSemanticsFragment fragment in fragment.interestingFragments) {
//            fragments.add(fragment);
//            fragment.addAncestor(this);
//            fragment.addTags(config.tagsForChildren);
//            if (config.explicitChildNodes || parent is! RenderObject) {
//            fragment.markAsExplicit();
//            continue;
//        }
//            if (!fragment.hasConfigForParent || producesForkingFragment)
//                continue;
//            if (!config.isCompatibleWith(fragment.config))
//                toBeMarkedExplicit.add(fragment);
//            for (_InterestingSemanticsFragment siblingFragment in fragments.sublist(0, fragments.length - 1)) {
//            if (!fragment.config.isCompatibleWith(siblingFragment.config)) {
//                toBeMarkedExplicit.add(fragment);
//                toBeMarkedExplicit.add(siblingFragment);
//            }
//        }
//        }
//        });
//
//        for (_InterestingSemanticsFragment fragment in toBeMarkedExplicit)
//        fragment.markAsExplicit();
//
//        _needsSemanticsUpdate = false;
//
//        _SemanticsFragment result;
//        if (parent is! RenderObject) {
//        assert(!config.hasBeenAnnotated);
//        assert(!mergeIntoParent);
//        result = new _RootSemanticsFragment(
//                owner: this,
//        dropsSemanticsOfPreviousSiblings: dropSemanticsOfPreviousSiblings,
//        );
//    } else if (producesForkingFragment) {
//        result = new _ContainerSemanticsFragment(
//                dropsSemanticsOfPreviousSiblings: dropSemanticsOfPreviousSiblings,
//        );
//    } else {
//        result = new _SwitchableSemanticsFragment(
//                config: config,
//        mergeIntoParent: mergeIntoParent,
//        owner: this,
//        dropsSemanticsOfPreviousSiblings: dropSemanticsOfPreviousSiblings,
//        );
//        if (config.isSemanticBoundary) {
//            final _SwitchableSemanticsFragment fragment = result;
//            fragment.markAsExplicit();
//        }
//    }
//
//        result.addAll(fragments);
//
//        return result;
//    }
//
//    /// Called when collecting the semantics of this node.
//    ///
//    /// The implementation has to return the children in paint order skipping all
//    /// children that are not semantically relevant (e.g. because they are
//    /// invisible).
//    ///
//    /// The default implementation mirrors the behavior of
//    /// [visitChildren()] (which is supposed to walk all the children).
//    void visitChildrenForSemantics(RenderObjectVisitor visitor) {
//        visitChildren(visitor);
//    }
//
//    /// Assemble the [SemanticsNode] for this [RenderObject].
//    ///
//    /// If [isSemanticBoundary] is true, this method is called with the `node`
//    /// created for this [RenderObject], the `config` to be applied to that node
//    /// and the `children` [SemanticNode]s that descendants of this RenderObject
//    /// have generated.
//    ///
//    /// By default, the method will annotate `node` with `config` and add the
//    /// `children` to it.
//    ///
//    /// Subclasses can override this method to add additional [SemanticsNode]s
//    /// to the tree. If new [SemanticsNode]s are instantiated in this method
//    /// they must be disposed in [clearSemantics].
//    void assembleSemanticsNode(
//            SemanticsNode node,
//    SemanticsConfiguration config,
//    Iterable<SemanticsNode> children,
//    ) {
//        assert(node == _semantics);
//        node.updateWith(config: config, childrenInInversePaintOrder: children);
//    }
//
//    // EVENTS
//
//    /// Override this method to handle pointer events that hit this render object.
//    @override
//    void handleEvent(PointerEvent event, covariant HitTestEntry entry) { }
//
//
//    // HIT TESTING
//
//    // RenderObject subclasses are expected to have a method like the following
//    // (with the signature being whatever passes for coordinates for this
//    // particular class):
//    //
//    // bool hitTest(HitTestResult result, { Offset position }) {
//    //   // If the given position is not inside this node, then return false.
//    //   // Otherwise:
//    //   // For each child that intersects the position, in z-order starting from
//    //   // the top, call hitTest() for that child, passing it /result/, and the
//    //   // coordinates converted to the child's coordinate origin, and stop at
//    //   // the first child that returns true.
//    //   // Then, add yourself to /result/, and return true.
//    // }
//    //
//    // If you add yourself to /result/ and still return false, then that means you
//    // will see events but so will objects below you.
//
//
//    /// Returns a human understandable name.
//    @override
//    String toStringShort() {
//        String header = describeIdentity(this);
//        if (_relayoutBoundary != null && _relayoutBoundary != this) {
//            int count = 1;
//            RenderObject target = parent;
//            while (target != null && target != _relayoutBoundary) {
//                target = target.parent;
//                count += 1;
//            }
//            header += ' relayoutBoundary=up$count';
//        }
//        if (_needsLayout)
//            header += ' NEEDS-LAYOUT';
//        if (_needsPaint)
//            header += ' NEEDS-PAINT';
//        if (!attached)
//            header += ' DETACHED';
//        return header;
//    }
//
//    @override
//    String toString({ DiagnosticLevel minLevel }) => toStringShort();
//
//    /// Returns a description of the tree rooted at this node.
//    /// If the prefix argument is provided, then every line in the output
//    /// will be prefixed by that string.
//    @override
//    String toStringDeep({
//        String prefixLineOne: '',
//        String prefixOtherLines: '',
//        DiagnosticLevel minLevel: DiagnosticLevel.debug,
//    }) {
//        RenderObject debugPreviousActiveLayout;
//        assert(() {
//            debugPreviousActiveLayout = _debugActiveLayout;
//            _debugActiveLayout = null;
//            return true;
//        }());
//        final String result = super.toStringDeep(
//                prefixLineOne: prefixLineOne,
//                prefixOtherLines: prefixOtherLines,
//                minLevel: minLevel,
//                );
//        assert(() {
//            _debugActiveLayout = debugPreviousActiveLayout;
//            return true;
//        }());
//        return result;
//    }
//
//    /// Returns a one-line detailed description of the render object.
//    /// This description is often somewhat long.
//    ///
//    /// This includes the same information for this RenderObject as given by
//    /// [toStringDeep], but does not recurse to any children.
//    @override
//    String toStringShallow({
//        String joiner: '; ',
//        DiagnosticLevel minLevel: DiagnosticLevel.debug,
//    }) {
//        RenderObject debugPreviousActiveLayout;
//        assert(() {
//            debugPreviousActiveLayout = _debugActiveLayout;
//            _debugActiveLayout = null;
//            return true;
//        }());
//        final String result = super.toStringShallow(joiner: joiner, minLevel: minLevel);
//        assert(() {
//            _debugActiveLayout = debugPreviousActiveLayout;
//            return true;
//        }());
//        return result;
//    }
//
//    @protected
//    @override
//    void debugFillProperties(DiagnosticPropertiesBuilder properties) {
//        properties.add(new DiagnosticsProperty<dynamic>('creator', debugCreator, defaultValue: null, level: DiagnosticLevel.debug));
//        properties.add(new DiagnosticsProperty<ParentData>('parentData', parentData, tooltip: _debugCanParentUseSize == true ? 'can use size' : null, missingIfNull: true));
//        properties.add(new DiagnosticsProperty<Constraints>('constraints', constraints, missingIfNull: true));
//        // don't access it via the "layer" getter since that's only valid when we don't need paint
//        properties.add(new DiagnosticsProperty<OffsetLayer>('layer', _layer, defaultValue: null));
//        properties.add(new DiagnosticsProperty<SemanticsNode>('semantics node', _semantics, defaultValue: null));
//        properties.add(new FlagProperty(
//                'isBlockingSemanticsOfPreviouslyPaintedNodes',
//                value: _semanticsConfiguration.isBlockingSemanticsOfPreviouslyPaintedNodes,
//        ifTrue: 'blocks semantics of earlier render objects below the common boundary',
//        ));
//        properties.add(new FlagProperty('isSemanticBoundary', value: _semanticsConfiguration.isSemanticBoundary, ifTrue: 'semantic boundary'));
//    }
//
//    @override
//    List<DiagnosticsNode> debugDescribeChildren() => <DiagnosticsNode>[];
//
//    /// Attempt to make this or a descendant RenderObject visible on screen.
//    ///
//    /// If [child] is provided, that [RenderObject] is made visible. If [child] is
//    /// omitted, this [RenderObject] is made visible.
//    void showOnScreen([RenderObject child]) {
//        if (parent is RenderObject) {
//            final RenderObject renderParent = parent;
//            renderParent.showOnScreen(child ?? this);
//        }
//    }
}