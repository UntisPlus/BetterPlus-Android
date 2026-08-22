
import androidx.compose.runtime.Composable
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.repository.MasterDataRepository

@Composable
fun ElementItem(
	element: PeriodElement,
	masterDataRepository: MasterDataRepository,
	content: @Composable (shortName: String, longName: String, isAllowed: Boolean) -> Unit
) {
	content(masterDataRepository.getShortName(element), masterDataRepository.getLongName(element), masterDataRepository.isAllowed(element))
}
