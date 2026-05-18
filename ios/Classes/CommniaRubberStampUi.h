#import <Tools/Tools.h>

NS_ASSUME_NONNULL_BEGIN

/// Custom-stamps-only rubber stamp picker: no built-in standard stamps, no Standard/Custom tab bar.
@interface CommniaRubberStampUi : NSObject

+ (void)applyHideStandardStampsForTool:(nullable PTTool *)tool
                          toolManager:(nullable PTToolManager *)toolManager;

+ (void)configureCustomStampsOnlyPicker:(PTRubberStampViewController *)viewController;

+ (void)applyStampOptions:(NSArray<PTCustomStampOption *> *)options
           toToolManager:(PTToolManager *)toolManager;

@end

NS_ASSUME_NONNULL_END