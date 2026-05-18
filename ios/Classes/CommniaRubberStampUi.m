#import "CommniaRubberStampUi.h"
#import <objc/runtime.h>

/// Defined in PdftronFlutterPlugin.m — shared stamp catalog loader.
extern NSArray<PTCustomStampOption *> *CommniaLoadPersistedCustomStampOptions(void);

static const void *kCommniaPickerPreparedKey = &kCommniaPickerPreparedKey;

@interface CommniaRubberStampUi (Private)

+ (void)applyPickerStateBeforeFirstLayout:(PTRubberStampViewController *)viewController;
+ (void)ensureAddStampButtonVisible:(PTRubberStampViewController *)viewController;
+ (void)applyStampCatalogToManager:(PTRubberStampManager *)manager;
+ (void)hideTabSegmentInSupplementaryView:(UIView *)view;

@end

@interface PTRubberStampViewController (CommniaPicker)
- (void)commnia_viewDidLoad;
- (void)commnia_viewDidLayoutSubviews;
- (NSInteger)commnia_collectionView:(UICollectionView *)collectionView
              numberOfItemsInSection:(NSInteger)section;
- (UICollectionReusableView *)commnia_collectionView:(UICollectionView *)collectionView
                    viewForSupplementaryElementOfKind:(NSString *)kind
                                          atIndexPath:(NSIndexPath *)indexPath;
- (CGSize)commnia_collectionView:(UICollectionView *)collectionView
                            layout:(UICollectionViewLayout *)layout
  referenceSizeForHeaderInSection:(NSInteger)section;
@end

@implementation PTRubberStampViewController (CommniaPicker)

- (void)commnia_viewDidLoad
{
    [CommniaRubberStampUi applyPickerStateBeforeFirstLayout:self];
    [self commnia_viewDidLoad];
    [CommniaRubberStampUi ensureAddStampButtonVisible:self];
}

- (void)commnia_viewDidLayoutSubviews
{
    [self commnia_viewDidLayoutSubviews];
    [CommniaRubberStampUi ensureAddStampButtonVisible:self];
}

- (NSInteger)commnia_collectionView:(UICollectionView *)collectionView
              numberOfItemsInSection:(NSInteger)section
{
    [CommniaRubberStampUi applyPickerStateBeforeFirstLayout:self];
    return [self commnia_collectionView:collectionView numberOfItemsInSection:section];
}

- (UICollectionReusableView *)commnia_collectionView:(UICollectionView *)collectionView
                    viewForSupplementaryElementOfKind:(NSString *)kind
                                          atIndexPath:(NSIndexPath *)indexPath
{
    UICollectionReusableView *view = [self commnia_collectionView:collectionView
                               viewForSupplementaryElementOfKind:kind
                                                     atIndexPath:indexPath];
    if ([kind isEqualToString:UICollectionElementKindSectionHeader]) {
        [CommniaRubberStampUi hideTabSegmentInSupplementaryView:view];
    }
    return view;
}

- (CGSize)commnia_collectionView:(UICollectionView *)collectionView
                            layout:(UICollectionViewLayout *)layout
  referenceSizeForHeaderInSection:(NSInteger)section
{
    (void)collectionView;
    (void)layout;
    (void)section;
    return CGSizeZero;
}

@end

@implementation CommniaRubberStampUi

+ (void)installCustomStampsOnlyPickerIfNeeded
{
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        Class klass = [PTRubberStampViewController class];
        [self commnia_swizzleInstanceMethodOnClass:klass
                                      fromSelector:@selector(viewDidLoad)
                                        toSelector:@selector(commnia_viewDidLoad)];
        [self commnia_swizzleInstanceMethodOnClass:klass
                                      fromSelector:@selector(viewDidLayoutSubviews)
                                        toSelector:@selector(commnia_viewDidLayoutSubviews)];
        [self commnia_swizzleInstanceMethodOnClass:klass
                                      fromSelector:@selector(collectionView:numberOfItemsInSection:)
                                        toSelector:@selector(commnia_collectionView:numberOfItemsInSection:)];
        [self commnia_swizzleInstanceMethodOnClass:klass
                                      fromSelector:@selector(collectionView:viewForSupplementaryElementOfKind:atIndexPath:)
                                        toSelector:@selector(commnia_collectionView:viewForSupplementaryElementOfKind:atIndexPath:)];
        [self commnia_swizzleInstanceMethodOnClass:klass
                                      fromSelector:@selector(collectionView:layout:referenceSizeForHeaderInSection:)
                                        toSelector:@selector(commnia_collectionView:layout:referenceSizeForHeaderInSection:)];
    });
}

+ (void)commnia_swizzleInstanceMethodOnClass:(Class)klass
                                fromSelector:(SEL)originalSelector
                                  toSelector:(SEL)swizzledSelector
{
    Method originalMethod = class_getInstanceMethod(klass, originalSelector);
    Method swizzledMethod = class_getInstanceMethod(klass, swizzledSelector);
    if (!originalMethod || !swizzledMethod) {
        return;
    }

    BOOL didAdd = class_addMethod(klass,
                                  originalSelector,
                                  method_getImplementation(swizzledMethod),
                                  method_getTypeEncoding(swizzledMethod));
    if (didAdd) {
        class_replaceMethod(klass,
                            swizzledSelector,
                            method_getImplementation(originalMethod),
                            method_getTypeEncoding(originalMethod));
    } else {
        method_exchangeImplementations(originalMethod, swizzledMethod);
    }
}

+ (PTRubberStampManager *)rubberStampManagerFromTool:(PTTool *)tool
                                         toolManager:(PTToolManager *)toolManager
{
    PTRubberStampManager *manager = nil;
    if ([tool respondsToSelector:@selector(rubberStampManager)]) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
        manager = [tool performSelector:@selector(rubberStampManager)];
#pragma clang diagnostic pop
    }
    if (!manager && toolManager) {
        @try {
            id candidate = [toolManager valueForKey:@"rubberStampManager"];
            if ([candidate isKindOfClass:[PTRubberStampManager class]]) {
                manager = candidate;
            }
        } @catch (__unused NSException *ex) {
        }
    }
    return manager;
}

+ (void)applyStampCatalogToManager:(PTRubberStampManager *)manager
{
    if (!manager) {
        return;
    }
    manager.standardStampOptions = @[];
    NSArray<PTCustomStampOption *> *persisted = CommniaLoadPersistedCustomStampOptions();
    if (persisted.count > 0) {
        manager.customStampOptions = persisted;
    }
}

+ (void)applyPickerStateBeforeFirstLayout:(PTRubberStampViewController *)viewController
{
    if (![viewController isKindOfClass:[PTRubberStampViewController class]]) {
        return;
    }

    NSNumber *prepared = objc_getAssociatedObject(viewController, kCommniaPickerPreparedKey);
    if (prepared.boolValue) {
        return;
    }

    PTRubberStampManager *manager = viewController.rubberStampManager;
    if (!manager) {
        return;
    }

    [self applyStampCatalogToManager:manager];

    if ([viewController respondsToSelector:@selector(setShowingStandardStamps:)]) {
        [viewController setValue:@NO forKey:@"showingStandardStamps"];
    }
    if ([viewController respondsToSelector:@selector(setEditingEnabled:)]) {
        [viewController setValue:@YES forKey:@"editingEnabled"];
    }

    objc_setAssociatedObject(viewController,
                             kCommniaPickerPreparedKey,
                             @YES,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

+ (void)ensureAddStampButtonVisible:(PTRubberStampViewController *)viewController
{
    if (![viewController isKindOfClass:[PTRubberStampViewController class]]) {
        return;
    }

    UIBarButtonItem *addButton = nil;
    if ([viewController respondsToSelector:@selector(addStampButton)]) {
        id value = [viewController valueForKey:@"addStampButton"];
        if ([value isKindOfClass:[UIBarButtonItem class]]) {
            addButton = value;
        }
    }
    if (!addButton) {
        return;
    }

    addButton.enabled = YES;
    addButton.tintColor = viewController.view.tintColor ?: [UIColor systemBlueColor];

    if ([addButton.customView isKindOfClass:[UIView class]]) {
        addButton.customView.hidden = NO;
        addButton.customView.alpha = 1.0;
    }

  // Keep Done in the nav bar; place + on the bottom toolbar (footer).
    if ([viewController respondsToSelector:@selector(doneButtonItem)]) {
        id value = [viewController valueForKey:@"doneButtonItem"];
        if ([value isKindOfClass:[UIBarButtonItem class]]) {
            viewController.navigationItem.rightBarButtonItem = value;
        }
    }

    UINavigationController *navigationController = viewController.navigationController;
    if (navigationController) {
        navigationController.toolbarHidden = NO;
        UIBarButtonItem *flex = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace
                                                                              target:nil
                                                                              action:nil];
        viewController.toolbarItems = @[flex, addButton];
    } else {
        // Modal without a navigation controller: anchor + to the bottom-right of the picker.
        UIView *hostView = viewController.view;
        if (hostView && addButton.customView == nil) {
            UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
            UIImage *plusImage = [UIImage systemImageNamed:@"plus"];
            if (plusImage) {
                [button setImage:plusImage forState:UIControlStateNormal];
            } else {
                [button setTitle:@"+" forState:UIControlStateNormal];
                button.titleLabel.font = [UIFont systemFontOfSize:28 weight:UIFontWeightRegular];
            }
            button.translatesAutoresizingMaskIntoConstraints = NO;
            button.tag = 0xC0A1;
            for (UIView *subview in hostView.subviews) {
                if (subview.tag == 0xC0A1) {
                    [subview removeFromSuperview];
                }
            }
            SEL action = @selector(addStampsButtonPressed:);
            if ([viewController respondsToSelector:action]) {
                [button addTarget:viewController
                           action:action
                 forControlEvents:UIControlEventTouchUpInside];
            }
            [hostView addSubview:button];
            UILayoutGuide *safe = hostView.safeAreaLayoutGuide;
            [NSLayoutConstraint activateConstraints:@[
                [button.trailingAnchor constraintEqualToAnchor:safe.trailingAnchor constant:-20],
                [button.bottomAnchor constraintEqualToAnchor:safe.bottomAnchor constant:-20],
                [button.widthAnchor constraintEqualToConstant:44],
                [button.heightAnchor constraintEqualToConstant:44],
            ]];
        }
    }
}

+ (void)applyStampOptions:(NSArray<PTCustomStampOption *> *)options
           toToolManager:(PTToolManager *)toolManager
{
    if (options.count == 0 || !toolManager) {
        return;
    }
    PTRubberStampManager *manager = [self rubberStampManagerFromTool:toolManager.tool
                                                          toolManager:toolManager];
    if (manager) {
        manager.standardStampOptions = @[];
        manager.customStampOptions = options;
    }
}

+ (void)applyHideStandardStampsForTool:(PTTool *)tool
                          toolManager:(PTToolManager *)toolManager
{
    [self installCustomStampsOnlyPickerIfNeeded];

    if (![tool isKindOfClass:[PTRubberStampCreate class]]) {
        return;
    }

    PTRubberStampManager *manager = [self rubberStampManagerFromTool:tool toolManager:toolManager];
    [self applyStampCatalogToManager:manager];
}

+ (void)configureCustomStampsOnlyPicker:(PTRubberStampViewController *)viewController
{
    [self applyPickerStateBeforeFirstLayout:viewController];
    [self ensureAddStampButtonVisible:viewController];

    UICollectionView *collectionView = viewController.collectionView;
    if (collectionView) {
        for (UIView *supplementary in [collectionView visibleSupplementaryViewsOfKind:UICollectionElementKindSectionHeader]) {
            [self hideTabSegmentInSupplementaryView:supplementary];
        }
    }
}

+ (void)hideTabSegmentInSupplementaryView:(UIView *)view
{
    if ([view respondsToSelector:@selector(segmentedControl)]) {
        UISegmentedControl *segmented = [view valueForKey:@"segmentedControl"];
        if ([segmented isKindOfClass:[UISegmentedControl class]]) {
            segmented.hidden = YES;
            segmented.alpha = 0;
        }
    }
    if ([view respondsToSelector:@selector(toolbar)]) {
        UIToolbar *toolbar = [view valueForKey:@"toolbar"];
        if ([toolbar isKindOfClass:[UIToolbar class]]) {
            toolbar.hidden = YES;
        }
    }
}

@end
